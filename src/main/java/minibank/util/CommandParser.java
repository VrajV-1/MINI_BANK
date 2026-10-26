package minibank.util;

import minibank.exception.InvalidCommandException;
import minibank.exception.InvalidAmountException;
import minibank.model.TransactionType;
import java.util.Locale;

public final class CommandParser {
    private CommandParser() {
    }

    public static Command parse(String line) throws InvalidCommandException,
            InvalidAmountException {
        if (line == null || line.isBlank()) {
            throw new InvalidCommandException("Command cannot be blank");
        }
        String[] parts = line.trim().split("\\s+");
        TransactionType type;
        try {
            type = TransactionType.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidCommandException("Unknown transaction type: " + parts[0]);
        }

        boolean transfer = type == TransactionType.TRANSFER;
        int expectedParts = transfer ? 4 : 3;
        if (parts.length != expectedParts) {
            String format = transfer
                    ? "TRANSFER <from-account> <to-account> <amount>"
                    : type + " <account> <amount>";
            throw new InvalidCommandException("Expected: " + format);
        }
        if (!Validator.isValidAccountNumber(parts[1])) {
            throw new InvalidCommandException("Invalid account number: " + parts[1]);
        }
        if (transfer && !Validator.isValidAccountNumber(parts[2])) {
            throw new InvalidCommandException("Invalid account number: " + parts[2]);
        }

        int amountIndex = transfer ? 3 : 2;
        long amount;
        try {
            amount = Long.parseLong(parts[amountIndex]);
        } catch (NumberFormatException exception) {
            throw new InvalidCommandException("Amount must be a whole number", exception);
        }
        Command command = new Command(type, parts[1],
                transfer ? parts[2] : null, amount);
        String[] errors = AnnotationValidator.validate(command);
        if (errors.length > 0) {
            throw new InvalidAmountException(String.join("; ", errors));
        }
        return command;
    }
}
