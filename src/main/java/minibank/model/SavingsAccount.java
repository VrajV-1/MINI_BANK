package minibank.model;

import minibank.exception.BankException;
import minibank.exception.InsufficientFundsException;
import java.math.BigInteger;

public class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;
    private final long minimumBalance;

    public SavingsAccount(Customer owner, long openingBalance, long minimumBalance) {
        super(owner, openingBalance);
        if (minimumBalance < 0) {
            throw new IllegalArgumentException("Minimum balance cannot be negative");
        }
        this.minimumBalance = minimumBalance;
    }

    public long getMinimumBalance() {
        return minimumBalance;
    }

    @Override
    public double interestRate() {
        return 4.0;
    }

    @Override
    public boolean canWithdraw(long amount) {
        long balance = currentBalance();
        return amount > 0 && balance >= minimumBalance
                && amount <= balance - minimumBalance;
    }

    @Override
    public synchronized BankException withdrawalFailure(long amount) {
        BigInteger available = BigInteger.valueOf(currentBalance())
                .subtract(BigInteger.valueOf(minimumBalance)).max(BigInteger.ZERO);
        return new InsufficientFundsException(calculateShortfall(amount, available));
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }
}
