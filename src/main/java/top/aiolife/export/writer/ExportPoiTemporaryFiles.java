package top.aiolife.export.writer;

import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.util.TempFileCreationStrategy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** POI 的临时策略为进程级；用线程作用域隔离并发任务，其他调用保留默认行为。 */
@Component
public class ExportPoiTemporaryFiles {
    private static final ThreadLocal<Path> DIRECTORY = new ThreadLocal<>();

    public ExportPoiTemporaryFiles() {
        TempFile.setTempFileCreationStrategy(
                new TempFileCreationStrategy() {
                    private final DefaultTempFileCreationStrategy fallback =
                            new DefaultTempFileCreationStrategy();

                    public File createTempFile(String prefix, String suffix) throws IOException {
                        Path directory = DIRECTORY.get();
                        return directory == null
                                ? fallback.createTempFile(prefix, suffix)
                                : Files.createTempFile(directory, prefix, suffix).toFile();
                    }

                    public File createTempDirectory(String prefix) throws IOException {
                        Path directory = DIRECTORY.get();
                        return directory == null
                                ? fallback.createTempDirectory(prefix)
                                : Files.createTempDirectory(directory, prefix).toFile();
                    }
                });
    }

    public AutoCloseable use(Path directory) {
        if (DIRECTORY.get() != null) throw new IllegalStateException("Nested POI workspace");
        DIRECTORY.set(directory);
        return DIRECTORY::remove;
    }
}
