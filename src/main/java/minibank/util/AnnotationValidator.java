package minibank.util;

import minibank.annotation.MaxLength;
import minibank.annotation.NotBlank;
import minibank.annotation.Positive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class AnnotationValidator {
    private AnnotationValidator() {
    }

    public static String[] validate(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Object must not be null");
        }
        List<String> errors = new ArrayList<>();
        Class<?> type = object.getClass();

        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(NotBlank.class)
                        && !field.isAnnotationPresent(MaxLength.class)
                        && !field.isAnnotationPresent(Positive.class)) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    throw new IllegalStateException(
                            "Cannot access field for validation: " + field.getName());
                }
                Object value;
                try {
                    value = field.get(object);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(
                            "Could not read field for validation: " + field.getName(),
                            exception);
                }

                if (field.isAnnotationPresent(NotBlank.class)
                        && (!(value instanceof String text) || text.isBlank())) {
                    errors.add(field.getName() + " must not be blank");
                }
                if (field.isAnnotationPresent(MaxLength.class)
                        && value instanceof String text
                        && text.length() > field.getAnnotation(MaxLength.class).value()) {
                    errors.add(field.getName() + " maximum length is "
                            + field.getAnnotation(MaxLength.class).value());
                }
                if (field.isAnnotationPresent(Positive.class)
                        && value instanceof Number number
                        && number.doubleValue() <= 0) {
                    errors.add(field.getName() + " "
                            + field.getAnnotation(Positive.class).message());
                }
            }
            type = type.getSuperclass();
        }
        return errors.toArray(String[]::new);
    }
}
