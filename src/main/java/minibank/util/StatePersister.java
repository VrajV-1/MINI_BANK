package minibank.util;

import minibank.model.Account;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StatePersister {
    private StatePersister() {
    }

    public static void save(Account[] accounts, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ObjectOutputStream output =
                     new ObjectOutputStream(Files.newOutputStream(file))) {
            output.writeObject(accounts);
        }
    }

    public static Account[] load(Path file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input =
                     new ObjectInputStream(Files.newInputStream(file))) {
            Object saved = input.readObject();
            if (!(saved instanceof Account[] accounts)) {
                throw new IOException("Saved bank data is not an account array");
            }
            for (Account account : accounts) {
                if (account == null) {
                    throw new IOException("Saved bank data contains an empty account");
                }
                Account.observeAccountNumber(account.getAccountNumber());
            }
            return accounts;
        }
    }
}
