package minibank.model;

import minibank.exception.AccountLockedException;
import minibank.exception.BankException;

public class FixedDepositAccount extends Account {
    private static final long serialVersionUID = 1L;

    public FixedDepositAccount(Customer owner, long openingBalance) {
        super(owner, openingBalance);
    }

    @Override
    public double interestRate() {
        return 7.0;
    }

    @Override
    public boolean canWithdraw(long amount) {
        return false;
    }

    @Override
    public String getAccountType() {
        return "Fixed deposit";
    }

    @Override
    public synchronized BankException withdrawalFailure(long amount) {
        return new AccountLockedException(
                "Fixed deposit account " + getAccountNumber() + " is locked");
    }
}
