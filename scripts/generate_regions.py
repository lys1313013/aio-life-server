#!/usr/bin/env python3
"""Regenerate the pinned four-level region seed; verify the source before writing."""
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "dc3a1d7acd85ca1b0979543e9259604142c52e8e"
SHA256 = "eaec154ce55f9683fbae09a21cea7d8523e4074f323602cf92b6840611139c5b"
SOURCE = f"https://raw.githubusercontent.com/modood/Administrative-divisions-of-China/{COMMIT}/dist/pcas-code.json"
HEADER = '-- 由scripts/generate_regions.py从固定版本公开目录生成，不手工编辑地区行。\n-- 目的：补齐2023年大陆31省级、342市级、2978县级、41352乡级目录；不含港澳台及村级。\n-- 前置：MySQL 8.0.16+；已创建system_region；来源及许可见docs/data/china-regions.md。\n-- 影响：仅补缺失目录，不写用户数据，不覆盖已维护名称、上下级或软删除状态。\n-- 重跑：编码或固定ID已存在时仅保持原ID，不覆盖记录。\n-- 执行：按数据库初始化顺序执行，遇错停止；每批500行，失败后可重跑补齐。\n-- 恢复：不自动删除目录；回退应用保留目录，数据更正使用独立迁移。\n'


def main():
    data = urlopen(SOURCE, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise ValueError("Region source checksum mismatch")
    rows = []
    codes = set()
    counts = [0, 0, 0, 0]

    def walk(items, parent="0", level=1):
        for item in items:
            code = item["code"]
            if not code.isdigit() or len(code) > 12 or code in codes or level > 4:
                raise ValueError("Invalid or duplicate region code")
            codes.add(code)
            counts[level - 1] += 1
            name = item["name"].replace("'", "''")
            rows.append(f"({900000000000000000 + int(code)},'{code}','{parent}','{name}',{level},'NBS-2023',0)")
            if item.get("children"):
                walk(item["children"], code, level + 1)

    walk(json.loads(data))
    if counts != [31, 342, 2978, 41352]:
        raise ValueError(f"Unexpected region coverage: {counts}")
    output = HEADER
    for index in range(0, len(rows), 500):
        output += "INSERT INTO `system_region` (`id`,`code`,`parent_code`,`name`,`level`,`data_version`,`is_deleted`) VALUES\n"
        output += ",\n".join(rows[index:index + 500]) + "\nON DUPLICATE KEY UPDATE `id`=`id`;\n"
    target = ROOT / "sql/2_ini_data/2026-10-22_system_regions.sql"
    target.write_text(output, encoding="utf-8")
    print(f"Generated {len(rows)} regions; verify the generated seed if source changes")


if __name__ == "__main__":
    main()
