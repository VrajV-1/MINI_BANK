package minibank.model;

import minibank.exception.BankException;

@FunctionalInterface
public interface WithdrawRule {
    boolean allow(Account account, long amount) throws BankException;
}
