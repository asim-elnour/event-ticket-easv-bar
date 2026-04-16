package dk.easv.eventTicketSystem.gui.details;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.SearchScope;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetailsPanelController implements ModelAware {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");

    @FXML
    private VBox emptyCard;
    @FXML
    private VBox userCard;
    @FXML
    private VBox customerCard;
    @FXML
    private VBox eventCard;
    @FXML
    private VBox coordinatorCard;
    @FXML
    private VBox ticketCard;
    @FXML
    private ScrollPane userScroll;
    @FXML
    private ScrollPane customerScroll;
    @FXML
    private ScrollPane eventScroll;
    @FXML
    private ScrollPane coordinatorScroll;
    @FXML
    private ScrollPane ticketScroll;

    @FXML
    private Label userNameValue;
    @FXML
    private Label userUsernameValue;
    @FXML
    private Label userEmailValue;
    @FXML
    private Label userPhoneValue;
    @FXML
    private Label userRolesValue;
    @FXML
    private Label userStatusValue;

    @FXML
    private Label customerNameValue;
    @FXML
    private Label customerEmailValue;
    @FXML
    private Label customerSinceValue;

    @FXML
    private Label eventNameValue;
    @FXML
    private Label eventLocationValue;
    @FXML
    private Label eventStartValue;
    @FXML
    private Label eventEndValue;
    @FXML
    private Label eventGuidanceValue;
    @FXML
    private Label eventNotesValue;
    @FXML
    private Label eventStatusValue;
    @FXML
    private VBox eventTicketTypesBox;

    @FXML
    private Label coordinatorNameValue;
    @FXML
    private Label coordinatorUsernameValue;
    @FXML
    private Label coordinatorEmailValue;
    @FXML
    private Label coordinatorRolesValue;
    @FXML
    private Label coordinatorEventValue;
    @FXML
    private Label coordinatorStatusValue;

    @FXML
    private Label ticketEventValue;
    @FXML
    private Label ticketCodeValue;
    @FXML
    private Label ticketCustomerValue;
    @FXML
    private Label ticketEmailValue;
    @FXML
    private Label ticketIssuedValue;
    @FXML
    private Label ticketRedeemedValue;
    @FXML
    private Label ticketStatusValue;
    @FXML
    private ImageView ticketBarcodeImage;
    @FXML
    private ImageView ticketQrImage;

    private AppModel model;
    private boolean initialized;
    private boolean listenersBound;

    @FXML
    public void initialize() {
        initialized = true;
        showCard(emptyCard);
        if (model != null) {
            bindModel();
        }
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        if (initialized) {
            bindModel();
        }
    }

    private void bindModel() {
        if (model == null) {
            showCard(emptyCard);
            return;
        }

        if (!listenersBound) {
            listenersBound = true;
            model.activeSearchScopeProperty().addListener((obs, oldValue, newValue) -> refreshView());
            model.selectedUserProperty().addListener((obs, oldValue, newValue) -> refreshView());
            model.selectedCustomerProperty().addListener((obs, oldValue, newValue) -> refreshView());
            model.selectedEventProperty().addListener((obs, oldValue, newValue) -> refreshView());
            model.selectedEventCoordinatorProperty().addListener((obs, oldValue, newValue) -> refreshView());
            model.selectedTicketProperty().addListener((obs, oldValue, newValue) -> refreshView());
        }

        refreshView();
    }

    private void refreshView() {
        if (model == null) {
            showCard(emptyCard);
            return;
        }

        SearchScope activeScope = model.getActiveSearchScope();
        if (activeScope == null) {
            activeScope = SearchScope.ADMINS;
        }

        switch (activeScope) {
            case ADMINS -> showUser(model.getSelectedUser());
            case CUSTOMERS -> showCustomer(model.getSelectedCustomer());
            case EVENTS -> showEvent(model.getSelectedEvent());
            case EVENT_COORDINATORS -> showCoordinator(model.getSelectedEventCoordinator());
            case TICKETS -> showTicket(model.getSelectedTicket());
        }
    }

    private void showUser(User user) {
        if (user == null) {
            showCard(emptyCard);
            return;
        }

        userNameValue.setText(safeText(user.getFullName()));
        userUsernameValue.setText(safeText(user.getUsername()));
        userEmailValue.setText(safeText(user.getEmail()));
        userPhoneValue.setText(safeText(user.getPhone()));
        userRolesValue.setText(formatRole(user.getRole()));
        userStatusValue.setText(formatUserStatus(user));
        showCard(userCard);
        resetScroll(userScroll);
    }

    private void showCustomer(Customer customer) {
        if (customer == null) {
            showCard(emptyCard);
            return;
        }

        customerNameValue.setText(safeText(customer.getName()));
        customerEmailValue.setText(safeText(customer.getEmail()));
        customerSinceValue.setText(formatDateTime(customer.getCreatedAt()));
        showCard(customerCard);
        resetScroll(customerScroll);
    }

    private void showEvent(Event event) {
        if (event == null) {
            showCard(emptyCard);
            return;
        }

        eventNameValue.setText(safeText(event.getName()));
        eventLocationValue.setText(safeText(event.getLocation()));
        eventStartValue.setText(formatDateTime(event.getStartTime()));
        eventEndValue.setText(formatDateTime(event.getEndTime()));
        eventGuidanceValue.setText(safeText(event.getLocationGuidance()));
        eventNotesValue.setText(safeText(event.getNotes()));
        eventStatusValue.setText(formatEventStatus(event));
        renderTicketTypes(event.getTicketTypesCopy());
        showCard(eventCard);
        resetScroll(eventScroll);
    }

    private void showCoordinator(User coordinator) {
        if (coordinator == null) {
            showCard(emptyCard);
            return;
        }

        coordinatorNameValue.setText(safeText(coordinator.getFullName()));
        coordinatorUsernameValue.setText(safeText(coordinator.getUsername()));
        coordinatorEmailValue.setText(safeText(coordinator.getEmail()));
        coordinatorRolesValue.setText(formatRole(coordinator.getRole()));
        coordinatorEventValue.setText(safeText(coordinator.getEventCoordinatorEventName()));
        coordinatorStatusValue.setText(coordinator.isEventCoordinatorRemoved() ? "Removed" : "Active");
        showCard(coordinatorCard);
        resetScroll(coordinatorScroll);
    }

    private void showTicket(Ticket ticket) {
        if (ticket == null) {
            showCard(emptyCard);
            return;
        }

        ticketEventValue.setText(safeText(ticket.getEventName()));
        ticketCodeValue.setText(safeText(ticket.getCode()));
        ticketCustomerValue.setText(safeText(ticket.getCustomerName()));
        ticketEmailValue.setText(safeText(ticket.getCustomerEmail()));
        ticketIssuedValue.setText(formatDateTime(ticket.getIssuedAt()));
        ticketRedeemedValue.setText(formatDateTime(ticket.getRedeemedAt()));
        ticketStatusValue.setText(ticket.getStatusLabel());
        renderBarcodes(ticket.getCode());
        showCard(ticketCard);
        resetScroll(ticketScroll);
    }

    private void renderTicketTypes(List<TicketCategory> ticketTypes) {
        eventTicketTypesBox.getChildren().clear();
        if (ticketTypes == null || ticketTypes.isEmpty()) {
            eventTicketTypesBox.getChildren().add(createListItem("No ticket types."));
            return;
        }

        int itemNumber = 1;
        for (TicketCategory ticketType : ticketTypes) {
            if (ticketType == null) {
                continue;
            }
            String item = itemNumber + ". " + safeText(ticketType.getName())
                    + "\n    Price: " + formatMoney(ticketType.getPrice())
                    + "\n    Seats: " + formatNumber(ticketType.getSeatCount())
                    + "\n    Status: " + (ticketType.isDeleted() ? "Deleted" : "Available");
            eventTicketTypesBox.getChildren().add(createListItem(item));
            itemNumber++;
        }

        if (eventTicketTypesBox.getChildren().isEmpty()) {
            eventTicketTypesBox.getChildren().add(createListItem("No ticket types."));
        }
    }

    private Label createListItem(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("details-list-item");
        label.setStyle("-fx-text-fill: #ffffff;");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private void renderBarcodes(String code) {
        ticketBarcodeImage.setImage(generateBarcodeImage(code, BarcodeFormat.CODE_128, 260, 90));
        ticketQrImage.setImage(generateBarcodeImage(code, BarcodeFormat.QR_CODE, 140, 140));
    }

    private WritableImage generateBarcodeImage(String value, BarcodeFormat format, int width, int height) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    value,
                    format,
                    width,
                    height,
                    Map.of(EncodeHintType.MARGIN, 1)
            );

            WritableImage image = new WritableImage(width, height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.getPixelWriter().setArgb(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showCard(VBox selectedCard) {
        setVisible(emptyCard, selectedCard == emptyCard);
        setVisible(userCard, selectedCard == userCard);
        setVisible(customerCard, selectedCard == customerCard);
        setVisible(eventCard, selectedCard == eventCard);
        setVisible(coordinatorCard, selectedCard == coordinatorCard);
        setVisible(ticketCard, selectedCard == ticketCard);
    }

    private void setVisible(VBox card, boolean visible) {
        if (card == null) {
            return;
        }
        card.setVisible(visible);
        card.setManaged(visible);
    }

    private void resetScroll(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        scrollPane.setVvalue(0);
        scrollPane.setHvalue(0);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "Not set";
        }
        return value.trim();
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "Not set";
        }
        return DATE_TIME_FORMAT.format(value);
    }

    private String formatRole(Role role) {
        if (role == null) {
            return "Not set";
        }
        return role.getRoleName();
    }

    private String formatUserStatus(User user) {
        if (user == null) {
            return "Not set";
        }
        return user.isDeleted() ? "Deleted" : "Active";
    }

    private String formatEventStatus(Event event) {
        if (event == null) {
            return "Not set";
        }
        return event.isDeleted() ? "Deleted" : "Active";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return MONEY_FORMAT.format(value);
    }

    private String formatNumber(Integer value) {
        return value == null ? "0" : Integer.toString(value);
    }
}
