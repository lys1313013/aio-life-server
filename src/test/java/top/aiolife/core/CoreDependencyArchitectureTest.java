package top.aiolife.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通用基础设施不能依赖业务模块的实体、Mapper 或服务。 */
class CoreDependencyArchitectureTest {
    private static final Pattern PROJECT_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?top\\.aiolife\\.([a-zA-Z0-9_]+)\\.");
    private static final Set<String> INFRASTRUCTURE_MODULES = Set.of("core", "config");

    @Test
    void 公共基础设施不反向依赖业务模块() throws Exception {
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(Path.of("src/main/java/top/aiolife/core"))) {
            var files = paths.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(files.isEmpty(), "必须扫描实际公共基础设施源码");
            for (var file : files) {
                var imports = PROJECT_IMPORT.matcher(Files.readString(file));
                while (imports.find()) {
                    if (!INFRASTRUCTURE_MODULES.contains(imports.group(1))) {
                        violations.add(file + " -> " + imports.group(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "业务实现应归所属模块：" + violations);
    }
}
