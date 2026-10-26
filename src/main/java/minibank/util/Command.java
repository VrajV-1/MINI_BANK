package minibank.util;

import minibank.annotation.NotBlank;
import minibank.annotation.Positive;
import minibank.model.TransactionType;

public record Command(
        TransactionType type,
        @NotBlank String accountNumber,
        String destinationAccountNumber,
        @Positive long amount) {
}
