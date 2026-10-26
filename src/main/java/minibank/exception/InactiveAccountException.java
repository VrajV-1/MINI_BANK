package minibank.exception;

public class InactiveAccountException extends BankException {
    private static final long serialVersionUID = 1L;

    public InactiveAccountException(String accountNumber) {
        super("Account is inactive: " + accountNumber);
    }
}
