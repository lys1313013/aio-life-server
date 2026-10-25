package top.aiolife.export.storage;

import org.springframework.http.HttpStatus;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.service.ExportException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.UUID;

/** 每个 attempt 独立目录；不拼接任何用户文件名。 */
public final class ExportWorkspace implements AutoCloseable {
    private final Path directory;
    private final long maximum;

    public ExportWorkspace(ExportProperties properties) throws IOException {
        Path root = Path.of(properties.getTemporaryDirectory()).toAbsolutePath();
        Files.createDirectories(root);
        directory =
                Files.createDirectory(
                        root.resolve(UUID.randomUUID().toString()),
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        maximum = properties.getMaxTemporaryBytes();
    }

    public Path directory() {
        return directory;
    }

    public Path file(String name) {
        if (!name.matches("[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Invalid workspace filename");
        return directory.resolve(name);
    }

    public void checkSize() throws IOException {
        try (var paths = Files.walk(directory)) {
            long size = 0;
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                size = Math.addExact(size, Files.size(path));
                if (size > maximum)
                    throw new ExportException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "EXPORT_RESOURCE_LIMIT",
                            "导出数据超过临时空间预算，请缩小范围");
            }
        }
    }

    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }
}
