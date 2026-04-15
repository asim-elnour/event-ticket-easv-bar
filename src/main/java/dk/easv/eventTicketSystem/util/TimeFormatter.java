package dk.easv.eventTicketSystem.util;

public final class TimeFormatter {

    private TimeFormatter() {}

    public static String formatSeconds(double sec) {
        int total = (int) sec;
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}