package minibank.model;

import minibank.annotation.Id;
import minibank.annotation.MaxLength;
import minibank.exception.AccountLockedException;
import minibank.exception.BankException;
import minibank.exception.InactiveAccountException;
import minibank.exception.InsufficientFundsException;
import minibank.exception.InvalidAmountException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Account
        implements Transactable, InterestBearing, Comparable<Account>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final AtomicInteger ACCOUNT_COUNTER = new AtomicInteger();

    @Id
    @MaxLength(10)
    private final String accountNumber;
    private final Customer owner;
    private long balance;
    private boolean active = true;

    protected Account(Customer owner, long openingBalance) {
        this(nextAccountNumber(), owner, openingBalance);
    }

    private Account(String accountNumber, Customer owner, long openingBalance) {
        if (owner == null) {
            throw new IllegalArgumentException("Account owner is required");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.balance = openingBalance;
        observeAccountNumber(accountNumber);
    }

    private static String nextAccountNumber() {
        return String.format("AC%04d", ACCOUNT_COUNTER.incrementAndGet());
    }

    public static void observeAccountNumber(String accountNumber) {
        if (accountNumber != null && accountNumber.matches("AC[0-9]{4,}")) {
            ACCOUNT_COUNTER.accumulateAndGet(
                    Integer.parseInt(accountNumber.substring(2)), Math::max);
        }
    }

    public final String getAccountNumber() {
        return accountNumber;
    }

    public final Customer getOwner() {
        return owner;
    }

    public final String getOwnerName() {
        return owner.getName();
    }

    @Override
    public final synchronized long getBalance() {
        return balance;
    }

    public final synchronized boolean isActive() {
        return active;
    }

    public final synchronized void close() throws BankException {
        if (balance != 0) {
            throw new BankException("An account must have a zero balance before closing");
        }
        active = false;
    }

    public abstract double interestRate();

    public abstract boolean canWithdraw(long amount);

    public abstract String getAccountType();

    @Override
    public final synchronized void deposit(long amount) throws BankException {
        validateDeposit(amount);
        try {
            balance = Math.addExact(balance, amount);
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException("Deposit exceeds the supported balance");
        }
    }

    public final synchronized void validateDeposit(long amount) throws BankException {
        if (!active) {
            throw new InactiveAccountException(accountNumber);
        }
        if (amount <= 0) {
            throw new InvalidAmountException("Deposit must be greater than zero");
        }
        try {
            Math.addExact(balance, amount);
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException("Deposit exceeds the supported balance");
        }
    }

    @Override
    public final synchronized boolean withdraw(long amount) throws BankException {
        validateWithdrawal(amount);
        balance = Math.subtractExact(balance, amount);
        return true;
    }

    public final synchronized void validateWithdrawal(long amount) throws BankException {
        if (!active) {
            throw new InactiveAccountException(accountNumber);
        }
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal must be greater than zero");
        }
        if (!canWithdraw(amount)) {
            throw withdrawalFailure(amount);
        }
    }

    public synchronized BankException withdrawalFailure(long amount) {
        BigInteger available = BigInteger.valueOf(balance).max(BigInteger.ZERO);
        return new InsufficientFundsException(calculateShortfall(amount, available));
    }

    protected final long calculateShortfall(long amount, BigInteger available) {
        return BigInteger.valueOf(amount).subtract(available)
                .max(BigInteger.ONE).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    protected final synchronized long currentBalance() {
        return balance;
    }

    @Override
    public final synchronized int compareTo(Account other) {
        return accountNumber.compareTo(other.accountNumber);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return accountNumber.equals(account.accountNumber);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(accountNumber);
    }

    @Override
    public synchronized String toString() {
        return accountNumber + " | " + getOwnerName() + " | "
                + getAccountType() + " | balance: " + balance;
    }
}
