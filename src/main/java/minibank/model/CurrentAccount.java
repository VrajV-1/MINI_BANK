package minibank.model;

import minibank.exception.BankException;
import minibank.exception.InsufficientFundsException;
import java.math.BigInteger;

public class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;
    private final long overdraftLimit;

    public CurrentAccount(Customer owner, long openingBalance, long overdraftLimit) {
        super(owner, openingBalance);
        if (overdraftLimit < 0) {
            throw new IllegalArgumentException("Overdraft limit cannot be negative");
        }
        this.overdraftLimit = overdraftLimit;
    }

    public long getOverdraftLimit() {
        return overdraftLimit;
    }

    @Override
    public double interestRate() {
        return 0.0;
    }

    @Override
    public boolean canWithdraw(long amount) {
        if (amount <= 0) {
            return false;
        }
        try {
            return Math.subtractExact(currentBalance(), amount) >= -overdraftLimit;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    @Override
    public synchronized BankException withdrawalFailure(long amount) {
        BigInteger available = BigInteger.valueOf(currentBalance())
                .add(BigInteger.valueOf(overdraftLimit)).max(BigInteger.ZERO);
        return new InsufficientFundsException(calculateShortfall(amount, available));
    }

    @Override
    public String getAccountType() {
        return "Current";
    }
}
