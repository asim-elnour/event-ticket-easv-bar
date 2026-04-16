package dk.easv.eventTicketSystem.util;

import java.util.regex.Pattern;

public final class UserValidationRules {

    public static final int MAX_TEXT_LENGTH = 255;
    public static final int MAX_PHONE_LENGTH = 50;
    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d+$");

    private UserValidationRules() {
    }

    public static String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalizeOptional(String value) {
        String normalized = normalizeRequired(value);
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isValidEmail(String email) {
        String normalized = normalizeRequired(email);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_TEXT_LENGTH
                && EMAIL_PATTERN.matcher(normalized).matches();
    }

    public static boolean isValidPhone(String phone) {
        String normalized = normalizeRequired(phone);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_PHONE_LENGTH
                && PHONE_PATTERN.matcher(normalized).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH && password.length() <= MAX_TEXT_LENGTH;
    }
}
