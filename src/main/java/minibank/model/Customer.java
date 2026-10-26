package minibank.model;

import minibank.annotation.Id;
import minibank.annotation.MaxLength;
import minibank.annotation.NotBlank;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class Customer implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final AtomicLong CUSTOMER_COUNTER = new AtomicLong(100);

    @Id
    private final String customerId;
    @NotBlank
    @MaxLength(60)
    private final String name;
    @NotBlank
    @MaxLength(100)
    private final String email;
    @NotBlank
    @MaxLength(15)
    private final String mobile;
    @MaxLength(10)
    private final String pan;
    private final Address address;

    public Customer(String name, String email, String mobile, String pan, Address address) {
        this(generateCustomerId(), name, email, mobile, pan, address);
    }

    private Customer(String customerId, String name, String email, String mobile,
            String pan, Address address) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.mobile = mobile;
        this.pan = pan == null ? "" : pan;
        this.address = address == null ? new Address("", "", "") : address;
        observeCustomerId(customerId);
    }

    private static String generateCustomerId() {
        return "CUST" + CUSTOMER_COUNTER.incrementAndGet();
    }

    public static void observeCustomerId(String customerId) {
        if (customerId != null && customerId.startsWith("CUST")) {
            try {
                CUSTOMER_COUNTER.accumulateAndGet(
                        Long.parseLong(customerId.substring(4)), Math::max);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid customer ID: " + customerId,
                        exception);
            }
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getPan() {
        return pan;
    }

    public Address getAddress() {
        return address;
    }

    @Override
    public Customer clone() {
        return new Customer(customerId, name, email, mobile, pan,
                new Address(address.line, address.city, address.pincode));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer customer)) {
            return false;
        }
        return customerId.equals(customer.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    @Override
    public String toString() {
        return customerId + " | " + name + " | " + email + " | " + mobile;
    }

    public static class Address implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String line;
        private final String city;
        private final String pincode;

        public Address(String line, String city, String pincode) {
            this.line = line == null ? "" : line;
            this.city = city == null ? "" : city;
            this.pincode = pincode == null ? "" : pincode;
        }

        public String getLine() {
            return line;
        }

        public String getCity() {
            return city;
        }

        public String getPincode() {
            return pincode;
        }

        @Override
        public String toString() {
            return java.util.stream.Stream.of(line, city, pincode)
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }
}
