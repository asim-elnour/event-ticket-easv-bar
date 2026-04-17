package dk.easv.eventTicketSystem.util;

public enum ViewType {
    LOGIN("/dk/easv/eventTicketSystem/gui/login/LoginView.fxml"),
    MAIN("/dk/easv/eventTicketSystem/gui/MainView.fxml"),

    DASHBOARD("/dk/easv/eventTicketSystem/gui/dashboard/AdminDashboard.fxml"),

    MESSAGE_DIALOG("/dk/easv/eventTicketSystem/gui/common/MessageDialog.fxml"),
    ACTION_CONFIRM_DIALOG("/dk/easv/eventTicketSystem/gui/common/ActionConfirmDialog.fxml"),
    SEARCH_BAR("/dk/easv/eventTicketSystem/gui/search/SearchBar.fxml"),

    ADMIN_COORDINATOR_DIALOG("/dk/easv/eventTicketSystem/gui/adminsAndCoordinators/AdminCoordinatorDialog.fxml"),

    EVENT_DIALOG("/dk/easv/eventTicketSystem/gui/events/EventDialog.fxml"),
    TICKET_TYPE_DIALOG("/dk/easv/eventTicketSystem/gui/events/TicketTypeDialog.fxml"),

    TICKET_DIALOG("/dk/easv/eventTicketSystem/gui/tickets/TicketDialog.fxml"),
    TICKET_PRINT_DIALOG("/dk/easv/eventTicketSystem/gui/tickets/TicketPrintDialog.fxml"),
    TICKET_REDEEM_DIALOG("/dk/easv/eventTicketSystem/gui/tickets/TicketRedeemDialog.fxml");

    private final String fxmlPath;

    ViewType(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}
