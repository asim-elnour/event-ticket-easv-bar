package dk.easv.eventTicketSystem.gui.tickets;

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
            lblTicketCode.setText("N/A");
            lblCustomer.setText("Customer: N/A");
            return;
        }

        lblTicketCode.setText(ticket.getCode() == null || ticket.getCode().isBlank() ? "N/A" : ticket.getCode());
        String customer = ticket.getCustomerName() == null || ticket.getCustomerName().isBlank()
                ? "N/A"
                : ticket.getCustomerName();
        lblCustomer.setText("Customer: " + customer);
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
}
