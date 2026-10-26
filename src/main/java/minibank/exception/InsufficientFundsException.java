package minibank.exception;

public class InsufficientFundsException extends BankException {
    private static final long serialVersionUID = 1L;
    private final long shortfall;

    public InsufficientFundsException(long shortfall) {
        super("Insufficient funds. Short by " + shortfall);
        this.shortfall = shortfall;
    }

    public long getShortfall() {
        return shortfall;
    }
}
