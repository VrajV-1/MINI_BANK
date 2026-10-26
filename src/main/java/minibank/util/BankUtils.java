package minibank.util;

import java.util.Locale;
import java.util.Objects;

public final class BankUtils {
    private BankUtils() {
    }

    public static String formatCurrency(long amount) {
        return "Rs. " + String.format(Locale.forLanguageTag("en-IN"), "%,d", amount);
    }

    public static String formatCurrency(double amount) {
        return "Rs. " + String.format(Locale.forLanguageTag("en-IN"), "%,.2f", amount);
    }

    public static <N extends Number> double average(N[] values) {
        Objects.requireNonNull(values, "Values must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("At least one value is required");
        }
        double total = 0;
        for (N value : values) {
            total += Objects.requireNonNull(value, "Values must not contain null")
                    .doubleValue();
        }
        return total / values.length;
    }
}
