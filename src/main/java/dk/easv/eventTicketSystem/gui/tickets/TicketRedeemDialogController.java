package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Ticket;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class TicketRedeemDialogController {

    @FXML
    private Label lblCode;
    @FXML
    private Label lblCustomerName;
    @FXML
    private Label lblCustomerEmail;

    private Ticket ticket;
    private boolean saved;

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
        if (ticket != null) {
            lblCode.setText(ticket.getCode());
            lblCustomerName.setText(ticket.getCustomerName());
            lblCustomerEmail.setText(ticket.getCustomerEmail());
        } else {
            lblCode.setText("");
            lblCustomerName.setText("");
            lblCustomerEmail.setText("");
        }
    }

    public Ticket getTicket() {
        return ticket;
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void onCancel() {
        saved = false;
        close();
    }

    @FXML
    private void onRedeem() {
        saved = true;
        close();
    }

    private void close() {
        Stage stage = (Stage) lblCode.getScene().getWindow();
        stage.close();
    }
}
