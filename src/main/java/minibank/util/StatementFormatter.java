package minibank.util;

import minibank.model.Account;
import java.util.List;

import static minibank.util.BankUtils.formatCurrency;

public final class StatementFormatter {
    private StatementFormatter() {
    }

    public static String buildStatement(Account account, List<String> transactions) {
        StringBuilder statement = new StringBuilder();
        statement.append("Account Statement").append(System.lineSeparator());
        statement.append("Account number: ").append(account.getAccountNumber())
                .append(System.lineSeparator());
        statement.append("Owner: ").append(account.getOwnerName())
                .append(System.lineSeparator());
        statement.append("Type: ").append(account.getAccountType())
                .append(System.lineSeparator());
        statement.append("Balance: ").append(formatCurrency(account.getBalance()))
                .append(System.lineSeparator());
        statement.append("Transactions:").append(System.lineSeparator());
        boolean found = false;
        for (String transaction : transactions) {
            if (java.util.Arrays.asList(transaction.split("\\s+"))
                    .contains(account.getAccountNumber())) {
                statement.append("  ").append(transaction).append(System.lineSeparator());
                found = true;
            }
        }
        if (!found) {
            statement.append("  No transactions yet").append(System.lineSeparator());
        }
        return statement.toString();
    }
}
