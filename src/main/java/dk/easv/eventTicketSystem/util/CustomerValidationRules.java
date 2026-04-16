package dk.easv.eventTicketSystem.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CustomerValidationRules {

    public static final int MAX_TEXT_LENGTH = 255;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[\\s@._%+\\-]+");

    private CustomerValidationRules() {
    }

    public static String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalizeEmailKey(String email) {
        return normalizeRequired(email).toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String email) {
        String normalized = normalizeRequired(email);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_TEXT_LENGTH
                && EMAIL_PATTERN.matcher(normalized).matches();
    }

    public static boolean namesMatch(String left, String right) {
        return normalizeRequired(left).equalsIgnoreCase(normalizeRequired(right));
    }

    public static boolean matchesSearch(String query, String... values) {
        List<String> tokens = tokenizeSearch(query);
        if (tokens.isEmpty()) {
            return true;
        }

        List<String> normalizedValues = new ArrayList<>();
        List<String> words = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeRequired(value).toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                normalizedValues.add(normalized);
                for (String word : WORD_SPLIT_PATTERN.split(normalized)) {
                    if (!word.isBlank()) {
                        words.add(word);
                    }
                }
            }
        }

        if (normalizedValues.isEmpty()) {
            return false;
        }

        for (String token : tokens) {
            boolean matched = normalizedValues.stream().anyMatch(value -> value.contains(token))
                    || words.stream().anyMatch(word -> word.startsWith(token));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static List<String> tokenizeSearch(String query) {
        String normalized = normalizeRequired(query).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
