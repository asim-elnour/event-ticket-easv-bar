package dk.easv.eventTicketSystem.gui.common;

public enum ActionDialogType {
    USER_DEACTIVATE(
            "Deactivate User",
            "Deactivate %s?",
            "This user will stay in the list and be shown as deactivated.",
            "Deactivate"
    ),
    USER_RESTORE(
            "Restore User",
            "Restore %s?",
            "This user will be restored to active status.",
            "Restore"
    ),
    EVENT_EDIT(
            "Edit Event",
            "Save changes to %s?",
            "The event will be updated.",
            "Save Changes"
    ),
    EVENT_DELETE(
            "Delete Event",
            "Delete %s?",
            "This event will be marked as deleted and shown as deleted in the list.",
            "Delete"
    ),
    EVENT_RESTORE(
            "Restore Event",
            "Restore %s?",
            "This event will be restored and set to planned status.",
            "Restore"
    ),
    TICKET_REFUND(
            "Refund Ticket",
            "Refund %s?",
            "This ticket will be refunded and stay visible when refunded rows are shown.",
            "Refund"
    ),
    TICKET_CATEGORY_DELETE(
            "Delete Ticket Type",
            "Delete %s?",
            "This ticket type will be marked as deleted and shown as deleted when deleted rows are visible.",
            "Delete"
    ),
    TICKET_CATEGORY_RESTORE(
            "Restore Ticket Type",
            "Restore %s?",
            "This ticket type will be restored to active status.",
            "Restore"
    ),
    EVENT_COORDINATOR_REMOVE(
            "Remove Coordinator",
            "Remove %s from this event?",
            "This coordinator will be marked as removed for the selected event.",
            "Remove"
    ),
    EVENT_COORDINATOR_RESTORE(
            "Restore Coordinator",
            "Restore %s for this event?",
            "This coordinator will be restored as active for the selected event.",
            "Restore"
    );

    private final String windowTitle;
    private final String headerTemplate;
    private final String contentText;
    private final String confirmButtonText;

    ActionDialogType(String windowTitle,
                     String headerTemplate,
                     String contentText,
                     String confirmButtonText) {
        this.windowTitle = windowTitle;
        this.headerTemplate = headerTemplate;
        this.contentText = contentText;
        this.confirmButtonText = confirmButtonText;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public String getHeaderText(String targetName) {
        return String.format(headerTemplate, resolveTargetName(targetName));
    }

    public String getContentText() {
        return contentText;
    }

    public String getConfirmButtonText() {
        return confirmButtonText;
    }

    private String resolveTargetName(String targetName) {
        return targetName == null || targetName.isBlank() ? "this item" : targetName;
    }
}
