package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.be.TicketCategory;

public final class EventValidationRules {

    public static final int MAX_TEXT_LENGTH = 255;
    public static final int MIN_SEAT_COUNT = 1;

    private EventValidationRules() {
    }

    public static String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static int countActiveSeats(Iterable<TicketCategory> categories) {
        int total = 0;
        if (categories == null) {
            return total;
        }

        for (TicketCategory category : categories) {
            if (category == null || category.isDeleted()) {
                continue;
            }
            Integer seatCount = category.getSeatCount();
            total += seatCount == null ? 0 : seatCount;
        }
        return total;
    }

    public static int countActiveTicketTypes(Iterable<TicketCategory> categories) {
        int total = 0;
        if (categories == null) {
            return total;
        }

        for (TicketCategory category : categories) {
            if (category != null && !category.isDeleted()) {
                total++;
            }
        }
        return total;
    }
}
