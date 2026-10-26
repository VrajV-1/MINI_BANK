package minibank.repository;

import minibank.annotation.Id;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Repository<T> {
    private final Map<String, T> items = new ConcurrentHashMap<>();

    public T save(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null");
        }
        Field idField = findIdField(item.getClass());
        if (idField == null) {
            throw new IllegalArgumentException(
                    "No field marked with @Id in " + item.getClass().getSimpleName());
        }
        if (!idField.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @Id field " + idField.getName());
        }
        try {
            Object value = idField.get(item);
            if (!(value instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("@Id field must contain a non-blank String");
            }
            items.put(id, item);
            return item;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read @Id field", exception);
        }
    }

    @SafeVarargs
    public final void saveAll(T... values) {
        for (T value : values) {
            save(value);
        }
    }

    public T findById(String id) {
        return id == null ? null : items.get(id);
    }

    public List<T> findAll() {
        return new ArrayList<>(items.values());
    }

    public boolean delete(String id) {
        return id != null && items.remove(id) != null;
    }

    public int size() {
        return items.size();
    }

    private static Field findIdField(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
