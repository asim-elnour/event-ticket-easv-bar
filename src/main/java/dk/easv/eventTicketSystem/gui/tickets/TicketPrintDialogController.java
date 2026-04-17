package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class TicketPrintDialogController {

    public enum TicketAction {
        EMAIL,
        PRINT
    }

    @FXML
    private Label lblTicketCode;
    @FXML
    private Label lblCustomer;

    private TicketAction selectedAction;

    public void setTicket(Ticket ticket) {
        if (ticket == null) {
            lblTicketCode.setText("Not set");
            lblCustomer.setText("Not set");
            return;
        }

        lblTicketCode.setText(resolveText(ticket.getCode()));
        lblCustomer.setText(resolveText(ticket.getCustomerName()));
    }

    public void setTicket(Ticket ticket, Event event) {
        setTicket(ticket);
    }

    public TicketAction getSelectedAction() {
        return selectedAction;
    }

    @FXML
    private void onCancel() {
        selectedAction = null;
        close();
    }

    @FXML
    private void onEmail() {
        selectedAction = TicketAction.EMAIL;
        close();
    }

    @FXML
    private void onPrint() {
        selectedAction = TicketAction.PRINT;
        close();
    }

    private void close() {
        Stage stage = (Stage) lblTicketCode.getScene().getWindow();
        stage.close();
    }

    private String resolveText(String value) {
        return value == null || value.isBlank() ? "Not set" : value.trim();
    }
}
