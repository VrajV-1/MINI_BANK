package minibank.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class ReportGenerator {
    private ReportGenerator() {
    }

    public static String generate(Path logDirectory, Path reportFile) throws IOException {
        long deposits = 0;
        long withdrawals = 0;
        StringBuilder details = new StringBuilder();

        if (Files.exists(logDirectory)) {
            try (DirectoryStream<Path> files =
                         Files.newDirectoryStream(logDirectory, "*.log")) {
                for (Path file : files) {
                    BasicFileAttributes attributes =
                            Files.readAttributes(file, BasicFileAttributes.class);
                    details.append(file.getFileName())
                            .append(" size=").append(attributes.size())
                            .append(" modified=").append(attributes.lastModifiedTime())
                            .append(System.lineSeparator());
                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) {
                                continue;
                            }
                            String[] parts = line.trim().split("\\s+");
                            try {
                                if ((parts.length == 3 && parts[0].equals("DEPOSIT"))
                                        || (parts.length == 3
                                        && parts[0].equals("WITHDRAW"))) {
                                    long amount = Long.parseLong(parts[2]);
                                    if (amount <= 0) {
                                        throw new NumberFormatException(
                                                "Transaction amount must be positive");
                                    }
                                    if (parts[0].equals("DEPOSIT")) {
                                        deposits = Math.addExact(deposits, amount);
                                    } else {
                                        withdrawals = Math.addExact(withdrawals, amount);
                                    }
                                } else if (parts.length == 3 && parts[0].equals("OPEN")) {
                                    if (Long.parseLong(parts[2]) < 0) {
                                        throw new NumberFormatException(
                                                "Opening balance cannot be negative");
                                    }
                                } else if (parts.length == 4
                                        && parts[0].equals("TRANSFER")) {
                                    long amount = Long.parseLong(parts[3]);
                                    if (amount <= 0) {
                                        throw new NumberFormatException(
                                                "Transfer amount must be positive");
                                    }
                                    deposits = Math.addExact(deposits, amount);
                                    withdrawals = Math.addExact(withdrawals, amount);
                                } else {
                                    throw new IOException("Invalid transaction record in "
                                            + file + ": " + line);
                                }
                            } catch (NumberFormatException | ArithmeticException exception) {
                                throw new IOException("Invalid transaction amount in "
                                        + file + ": " + line, exception);
                            }
                        }
                    }
                }
            }
        }

        long netChange;
        try {
            netChange = Math.subtractExact(deposits, withdrawals);
        } catch (ArithmeticException exception) {
            throw new IOException("Transaction totals exceed the supported range",
                    exception);
        }
        String report = details
                + "Total deposits: " + deposits + System.lineSeparator()
                + "Total withdrawals: " + withdrawals + System.lineSeparator()
                + "Net change: " + netChange + System.lineSeparator();
        Path parent = reportFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(reportFile)) {
            writer.write(report);
        }
        return report;
    }
}
