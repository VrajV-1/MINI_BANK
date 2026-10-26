package minibank.util;

import minibank.exception.ValidationException;
import minibank.model.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Validator {
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern IFSC_PATTERN =
            Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^AC[0-9]{4,8}$");

    private Validator() {
    }

    public static boolean isValidMobile(String value) {
        return value != null && MOBILE_PATTERN.matcher(value).matches();
    }

    public static boolean isValidEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }

    public static boolean isValidPan(String value) {
        return value != null && PAN_PATTERN.matcher(value).matches();
    }

    public static boolean isValidIfsc(String value) {
        return value != null && IFSC_PATTERN.matcher(value).matches();
    }

    public static boolean isValidAccountNumber(String value) {
        return value != null && ACCOUNT_PATTERN.matcher(value).matches();
    }

    public static void validateCustomer(Customer customer) throws ValidationException {
        List<String> errors = new ArrayList<>(
                List.of(AnnotationValidator.validate(customer)));
        if (!isValidEmail(customer.getEmail())) {
            errors.add("email format is invalid");
        }
        if (!isValidMobile(customer.getMobile())) {
            errors.add("mobile must be a valid 10-digit number");
        }
        if (!customer.getPan().isBlank() && !isValidPan(customer.getPan())) {
            errors.add("PAN format is invalid");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
