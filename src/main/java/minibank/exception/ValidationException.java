package minibank.exception;

import java.util.List;

public class ValidationException extends BankException {
    private static final long serialVersionUID = 1L;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
    }
}
