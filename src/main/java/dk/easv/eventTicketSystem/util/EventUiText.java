package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.be.Event;

public final class EventUiText {

    private EventUiText() {
    }

    public static String statusLabel(Event event) {
        if (event == null) {
            return "";
        }
        return event.isDeleted() ? "Deleted" : "Active";
    }
}
