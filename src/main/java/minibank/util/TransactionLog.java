package minibank.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class TransactionLog {
    private final Path directory;
    private final Path logFile;

    public TransactionLog(Path dataDirectory) {
        this.directory = dataDirectory.resolve("logs");
        this.logFile = directory.resolve("transactions.log");
    }

    public synchronized void append(String line) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(logFile, line + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<String> readAll() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory, "*.log")) {
            for (Path file : files) {
                lines.addAll(Files.readAllLines(file));
            }
        }
        return lines;
    }

    public Path getDirectory() {
        return directory;
    }
}
