package top.aiolife.migration;

import java.nio.file.*;

/** 未来迁移和契约测试只改临时项目，绝不改工作区 SQL。 */
final class TestProject {
    private TestProject() {}
    static Path copy(Path target) throws Exception {
        Path source = MigrationProject.root();
        for (String directory : new String[]{"sql/migrations", "tools/flyway/src/main/java"}) {
            try (var paths = Files.walk(source.resolve(directory))) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    Path destination = target.resolve(source.relativize(file));
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
        Files.copy(source.resolve("tools/flyway/pom.xml"), target.resolve("tools/flyway/pom.xml"));
        return target;
    }
}
