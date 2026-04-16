package dk.easv.eventTicketSystem.gui.model;

public enum DataViewMode {
    SELECTED_EVENT("Selected Event"),
    ALL_EVENTS("All Events");

    private final String label;

    DataViewMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
