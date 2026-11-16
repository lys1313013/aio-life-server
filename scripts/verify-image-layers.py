#!/usr/bin/env python3
"""构建仅业务字节码不同的镜像，检查 OCI 层复用及真实入口启动，不连接业务服务。"""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PROBE_CLASS = "top.aiolife.imagecheck.LayerProbe"
PROBE_SOURCE = """package top.aiolife.imagecheck;
public class LayerProbe {
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("image.layer.probe")) throw new AssertionError("JAVA_OPTS");
        if (!"appuser".equals(System.getProperty("user.name"))) throw new AssertionError("USER");
        Class.forName("org.springframework.boot.SpringApplication");
        System.out.println("LAYER_PROBE_A");
        System.exit(0);
    }
}
"""


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def probe_jar(original, target, bytecode, variant):
    """依赖字节保持不变，改变业务 class 和 ZIP 时间，模拟重新编译打包。"""
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(target, "w") as dest:
        for entry in source.infolist():
            data = source.read(entry)
            if entry.filename == "META-INF/MANIFEST.MF":
                data, count = re.subn(
                    rb"(?m)^Start-Class: [^\r\n]+",
                    ("Start-Class: " + PROBE_CLASS).encode(), data,
                )
                if count != 1:
                    raise ValueError("Expected one Spring Boot Start-Class manifest entry")
            info = copy.copy(entry)
            info.date_time = (2026, 1, 1 if variant == "A" else 2, 0, 0, 0)
            dest.writestr(info, data)
        dest.writestr("BOOT-INF/classes/" + PROBE_CLASS.replace(".", "/") + ".class",
                      bytecode.replace(b"LAYER_PROBE_A", ("LAYER_PROBE_" + variant).encode()))


def oci_image(path):
    with tarfile.open(path) as archive:
        def read_json(name):
            return json.load(archive.extractfile(name))

        def blob(descriptor):
            algorithm, digest = descriptor["digest"].split(":", 1)
            return read_json(f"blobs/{algorithm}/{digest}")

        index = read_json("index.json")
        if len(index["manifests"]) != 1:
            raise AssertionError("Expected one platform and no provenance manifests")
        manifest = blob(index["manifests"][0])
        config = blob(manifest["config"])
        history = [item for item in config["history"] if not item.get("empty_layer")]
        if len(history) != len(manifest["layers"]):
            raise AssertionError("Layer/history count mismatch")
        return [(layer, item["created_by"]) for layer, item in zip(manifest["layers"], history)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "target/image-layer-check")
    parser.add_argument("--platform", default="linux/amd64")
    parser.add_argument("--builder")
    args = parser.parse_args()
    original = args.jar.resolve(strict=True)
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    source = output / "LayerProbe.java"
    source.write_text(PROBE_SOURCE)
    run(["javac", "--release", "17", "-d", str(output / "classes"), str(source)])
    bytecode = (output / "classes" / (PROBE_CLASS.replace(".", "/") + ".class")).read_bytes()
    if bytecode.count(b"LAYER_PROBE_A") != 1:
        raise AssertionError("Expected one bytecode marker")

    images = []
    try:
        versions = []
        for variant in ("A", "B"):
            context = output / variant
            context.mkdir(exist_ok=True)
            probe_jar(original, context / "app.jar", bytecode, variant)
            shutil.copy2(ROOT / "Dockerfile.runtime", context / "Dockerfile")
            tag = "aio-life-image-check:" + uuid.uuid4().hex
            images.append(tag)
            oci = output / f"{variant}.oci.tar"
            command = ["docker", "buildx", "build", "--platform", args.platform,
                       "--provenance=false", "--sbom=false", "--progress=plain",
                       # 每次重新解包，防止仅靠解包缓存使测试假通过。
                       "--no-cache-filter=extractor", "--tag", tag,
                       "--output", f"type=oci,dest={oci}", "--output", "type=docker"]
            if args.builder:
                command.extend(["--builder", args.builder])
            run(command + [str(context)])
            versions.append(oci_image(oci))

        before, after = versions
        if len(before) != len(after):
            raise AssertionError("Layer count changed")
        report = {"platform": args.platform, "layers": [], "changed_bytes": 0}
        found_agent = found_dependencies = found_application = False
        for (old, old_command), (new, command) in zip(before, after):
            if old_command != command:
                raise AssertionError("Build instructions changed between probe images")
            application = "/extracted/application/" in command
            found_application |= application
            found_agent |= "/opt/skywalking-agent/" in command
            found_dependencies |= "/extracted/dependencies/" in command
            changed = old["digest"] != new["digest"]
            if changed != application:
                raise AssertionError(f"Unexpected layer change={changed}: {command}")
            report["layers"].append({"command": command, "digest": new["digest"],
                                     "size": new["size"], "changed": changed})
            if changed:
                report["changed_bytes"] += new["size"]
            print(f"{'CHANGED' if changed else 'REUSED ':7} {new['size'] / 1e6:8.3f} MB {command}", flush=True)
        if not (found_agent and found_dependencies and found_application):
            raise AssertionError("Missing agent, dependencies or application layer")

        # 使用镜像原始入口和真实依赖，验证精简 JAR、非 root、JAVA_OPTS 与 Agent 加载。
        for agent in (False, True):
            container = "aio-life-layer-probe-" + uuid.uuid4().hex
            command = ["docker", "run", "--rm", "--name", container,
                       "--network", "none", "--platform", args.platform,
                       "-e", "JAVA_OPTS=-Xms32m -Xmx128m -Dimage.layer.probe=true"]
            if agent:
                command.extend(["-e", "JAVA_TOOL_OPTIONS=-javaagent:/opt/skywalking-agent/skywalking-agent.jar",
                                "-e", "SW_LOGGING_OUTPUT=CONSOLE",
                                "-e", "SW_AGENT_COLLECTOR_BACKEND_SERVICES=127.0.0.1:11800"])
            try:
                result = run(command + [images[-1]], capture_output=True, text=True, timeout=60)
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                print(error.stdout or "", flush=True)
                print(error.stderr or "", flush=True)
                raise
            finally:
                # docker run 客户端超时不保证容器退出，只清理本次命名的探针容器。
                subprocess.run(["docker", "rm", "-f", container], check=False,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if "LAYER_PROBE_B" not in result.stdout:
                raise AssertionError("Probe entry point did not execute")
            if agent and "SkyWalking agent version:" not in result.stderr + result.stdout:
                raise AssertionError("Agent was not loaded")
            print(f"PASS entrypoint, dependencies, appuser, JAVA_OPTS; agent={agent}", flush=True)
        report["smoke"] = {"without_agent": "passed", "with_agent": "passed"}
        with original.open("rb") as jar_file:
            report["input_jar_sha256"] = hashlib.file_digest(jar_file, "sha256").hexdigest()
        (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(f"PASS only application layer changes: {report['changed_bytes'] / 1e6:.3f} MB", flush=True)
    finally:
        for tag in images:
            subprocess.run(["docker", "image", "rm", tag], check=False, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
