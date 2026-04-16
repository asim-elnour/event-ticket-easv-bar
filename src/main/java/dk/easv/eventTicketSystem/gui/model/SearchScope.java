package dk.easv.eventTicketSystem.gui.model;

public enum SearchScope {
    ADMINS("Users"),
    EVENTS("Events"),
    EVENT_COORDINATORS("Event Coordinators"),
    TICKETS("Tickets"),
    CUSTOMERS("Customers");

    private final String label;

    SearchScope(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
