package minibank.exception;

public class InvalidCommandException extends BankException {
    private static final long serialVersionUID = 1L;

    public InvalidCommandException(String message) {
        super(message);
    }

    public InvalidCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
