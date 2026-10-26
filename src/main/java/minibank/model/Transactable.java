package minibank.model;

import minibank.exception.BankException;

public interface Transactable {
    void deposit(long amount) throws BankException;

    boolean withdraw(long amount) throws BankException;
}
