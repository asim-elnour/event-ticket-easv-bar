package dk.easv.eventTicketSystem.gui.events;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.EventValidationRules;
import dk.easv.eventTicketSystem.util.ViewType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventDialogController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtLocation;
    @FXML
    private TextField txtGuidance;
    @FXML
    private TextArea txtNotes;
    @FXML
    private DatePicker dpStartDate;
    @FXML
    private TextField txtStartTime;
    @FXML
    private DatePicker dpEndDate;
    @FXML
    private TextField txtEndTime;
    @FXML
    private TableView<TicketCategory> ticketTypesTable;
    @FXML
    private TableColumn<TicketCategory, String> colTicketTypeName;
    @FXML
    private TableColumn<TicketCategory, BigDecimal> colTicketTypePrice;
    @FXML
    private TableColumn<TicketCategory, Integer> colTicketTypeSeats;
    @FXML
    private TableColumn<TicketCategory, Integer> colTicketTypeSold;
    @FXML
    private TableColumn<TicketCategory, String> colTicketTypeStatus;
    @FXML
    private Button btnToggleDeletedTicketTypes;
    @FXML
    private Button btnEditTicketType;
    @FXML
    private Button btnDeleteTicketType;
    @FXML
    private Label lblTotalSeats;
    @FXML
    private Label lblTotalSold;
    @FXML
    private Label errName;
    @FXML
    private Label errLocation;
    @FXML
    private Label errStartDate;
    @FXML
    private Label errStartTime;
    @FXML
    private Label errEndDate;
    @FXML
    private Label errEndTime;
    @FXML
    private Label errTicketTypes;

    private final ObservableList<TicketCategory> ticketTypes = FXCollections.observableArrayList();
    private final FilteredList<TicketCategory> visibleTicketTypes = new FilteredList<>(ticketTypes, category -> true);
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
    private List<TicketCategory> originalTicketTypes = List.of();

    private Event event;
    private boolean saved;
    private long coordinatorId;
    private boolean showDeletedTicketTypes = true;
    private boolean validationFeedbackEnabled;

    @FXML
    public void initialize() {
        txtStartTime.setPromptText("HH:mm");
        txtEndTime.setPromptText("HH:mm");

        colTicketTypeName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colTicketTypePrice.setCellValueFactory(cd -> cd.getValue().priceProperty());
        colTicketTypeSeats.setCellValueFactory(cd -> cd.getValue().seatCountProperty());
        colTicketTypeSold.setCellValueFactory(cd -> cd.getValue().soldCountProperty());
        colTicketTypeStatus.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().isDeleted() ? "Deleted" : "Active"
        ));
        colTicketTypePrice.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : currencyFormat.format(value));
            }
        });

        ticketTypesTable.setItems(visibleTicketTypes);
        ticketTypesTable.setMinHeight(240);
        ticketTypesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ticketTypesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                updateTicketTypeButtonState(newValue));
        btnEditTicketType.setDisable(true);
        btnDeleteTicketType.setDisable(true);
        updateTicketTypeButtonState(null);
        updateTicketTypeFilter();
        clearValidationMessages();
        updateTicketTypeSummary();

        setupLiveValidation();
    }

    public void setCoordinatorId(long coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    public void setEvent(Event event) {
        this.event = event;
        validationFeedbackEnabled = false;
        if (event != null) {
            txtName.setText(event.getName());
            txtLocation.setText(event.getLocation());
            txtGuidance.setText(event.getLocationGuidance());
            txtNotes.setText(event.getNotes());

            LocalDateTime start = event.getStartTime();
            if (start != null) {
                dpStartDate.setValue(start.toLocalDate());
                txtStartTime.setText(TIME_FORMAT.format(start));
            }

            LocalDateTime end = event.getEndTime();
            if (end != null) {
                dpEndDate.setValue(end.toLocalDate());
                txtEndTime.setText(TIME_FORMAT.format(end));
            }

            originalTicketTypes = loadTicketTypesForEvent(event);
            ticketTypes.setAll(copyTicketTypes(originalTicketTypes));
            updateTicketTypeFilter();
            updateTicketTypeSummary();
            clearValidationMessages();
            return;
        }

        txtName.clear();
        txtLocation.clear();
        txtGuidance.clear();
        txtNotes.clear();
        dpStartDate.setValue(null);
        txtStartTime.clear();
        dpEndDate.setValue(null);
        txtEndTime.clear();
        originalTicketTypes = List.of();
        ticketTypes.clear();
        updateTicketTypeFilter();
        updateTicketTypeSummary();
        clearValidationMessages();
    }

    public Event getEvent() {
        return event;
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void onAddTicketType() {
        TicketCategory draft = new TicketCategory();
        draft.setPrice(BigDecimal.ZERO);
        draft.setSeatCount(EventValidationRules.MIN_SEAT_COUNT);

        TicketCategory created = showTicketTypeDialog(draft, false);
        if (created != null) {
            created.setDeleted(false);
            ticketTypes.add(created);
            updateTicketTypeSummary();
            validateTicketTypes();
            updateTicketTypeFilter();
        }
    }

    @FXML
    private void onEditTicketType() {
        TicketCategory selected = ticketTypesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Edit Ticket Type", null, "Please select a ticket type to edit.");
            return;
        }

        TicketCategory edited = showTicketTypeDialog(selected.copy(), true);
        if (edited != null) {
            selected.restoreFrom(edited);
            ticketTypesTable.refresh();
            updateTicketTypeSummary();
            validateTicketTypes();
            updateTicketTypeFilter();
        }
    }

    @FXML
    private void onDeleteTicketType() {
        TicketCategory selected = ticketTypesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Delete Ticket Type", null, "Please select a ticket type to delete.");
            return;
        }

        boolean shouldDelete = !selected.isDeleted();
        ActionDialogType dialogType = shouldDelete
                ? ActionDialogType.TICKET_CATEGORY_DELETE
                : ActionDialogType.TICKET_CATEGORY_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getName(), txtName.getScene().getWindow())) {
            return;
        }

        selected.setDeleted(shouldDelete);
        ticketTypesTable.refresh();
        updateTicketTypeSummary();
        validateTicketTypes();
        updateTicketTypeFilter();
    }

    @FXML
    private void onToggleDeletedTicketTypes() {
        showDeletedTicketTypes = !showDeletedTicketTypes;
        updateTicketTypeFilter();
    }

    @FXML
    private void onCancel() {
        saved = false;
        close();
    }

    @FXML
    private void onSave() {
        validationFeedbackEnabled = true;
        if (!validateAll()) {
            return;
        }

        List<RefundImpact> refundImpacts = buildRefundImpacts();
        if (!refundImpacts.isEmpty() && !confirmRefundImpacts(refundImpacts)) {
            return;
        }

        LocalDateTime start = parseDateTime(dpStartDate.getValue(), txtStartTime.getText());
        LocalDateTime end = hasEndInput()
                ? parseDateTime(dpEndDate.getValue(), txtEndTime.getText())
                : null;

        if (event == null) {
            event = new Event();
            event.setCoordinatorId(coordinatorId);
            event.setCreatedByUserId(coordinatorId);
        }

        event.setName(EventValidationRules.normalizeRequired(txtName.getText()));
        event.setLocation(EventValidationRules.normalizeRequired(txtLocation.getText()));
        event.setLocationGuidance(EventValidationRules.normalizeOptional(txtGuidance.getText()));
        event.setNotes(EventValidationRules.normalizeOptional(txtNotes.getText()));
        event.setStartTime(start);
        event.setEndTime(end);
        event.setUpdatedAt(LocalDateTime.now());
        event.setTicketTypes(ticketTypes);
        saved = true;
        close();
    }

    private List<TicketCategory> loadTicketTypesForEvent(Event event) {
        return event == null ? List.of() : event.getTicketTypesCopy();
    }

    private List<TicketCategory> copyTicketTypes(List<TicketCategory> categories) {
        List<TicketCategory> copies = new ArrayList<>();
        if (categories == null) {
            return copies;
        }
        for (TicketCategory category : categories) {
            if (category != null) {
                copies.add(category.copy());
            }
        }
        return copies;
    }

    private TicketCategory showTicketTypeDialog(TicketCategory draft, boolean isEdit) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_TYPE_DIALOG.getFxmlPath()));

        try {
            Parent root = loader.load();
            TicketTypeDialogController controller = loader.getController();
            controller.setTicketType(draft);

            Stage stage = new Stage();
            stage.setTitle(isEdit ? "Edit Ticket Type" : "Add Ticket Type");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (txtName != null && txtName.getScene() != null) {
                stage.initOwner(txtName.getScene().getWindow());
            }

            stage.setScene(new Scene(root));
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                return controller.getTicketType();
            }
        } catch (IOException ex) {
            DialogUtils.showError("Ticket Type Dialog", null, "Unable to open ticket type dialog.");
        }

        return null;
    }

    private void setupLiveValidation() {
        txtName.textProperty().addListener((obs, oldValue, newValue) -> validateName());
        txtLocation.textProperty().addListener((obs, oldValue, newValue) -> validateLocation());
        dpStartDate.valueProperty().addListener((obs, oldValue, newValue) -> {
            validateStartDate();
            validateDateRange();
        });
        txtStartTime.textProperty().addListener((obs, oldValue, newValue) -> {
            validateStartTime();
            validateDateRange();
        });
        dpEndDate.valueProperty().addListener((obs, oldValue, newValue) -> {
            validateEndDate();
            validateDateRange();
        });
        txtEndTime.textProperty().addListener((obs, oldValue, newValue) -> {
            validateEndTime();
            validateDateRange();
        });
    }

    private void close() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.close();
    }

    private boolean validateAll() {
        boolean ok = true;
        ok = validateName() && ok;
        ok = validateLocation() && ok;
        ok = validateStartDate() && ok;
        ok = validateStartTime() && ok;
        ok = validateEndDate() && ok;
        ok = validateEndTime() && ok;
        ok = validateDateRange() && ok;
        ok = validateTicketTypes() && ok;
        return ok;
    }

    private boolean validateName() {
        String value = EventValidationRules.normalizeRequired(txtName.getText());
        if (value.isEmpty()) {
            showValidationMessage(errName, "Name is required.");
            return false;
        }
        if (value.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            showValidationMessage(errName, "Name too long (max " + EventValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }
        hideValidationMessage(errName);
        return true;
    }

    private boolean validateLocation() {
        String value = EventValidationRules.normalizeRequired(txtLocation.getText());
        if (value.isEmpty()) {
            showValidationMessage(errLocation, "Location is required.");
            return false;
        }
        if (value.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            showValidationMessage(errLocation, "Location too long (max " + EventValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }
        hideValidationMessage(errLocation);
        return true;
    }

    private boolean validateStartDate() {
        if (dpStartDate.getValue() == null) {
            showValidationMessage(errStartDate, "Start date is required.");
            return false;
        }
        hideValidationMessage(errStartDate);
        return true;
    }

    private boolean validateStartTime() {
        String value = txtStartTime.getText();
        if (value == null || value.isBlank()) {
            showValidationMessage(errStartTime, "Start time is required.");
            return false;
        }
        if (!isValidTime(value)) {
            showValidationMessage(errStartTime, "Use HH:mm format.");
            return false;
        }
        hideValidationMessage(errStartTime);
        return true;
    }

    private boolean validateEndDate() {
        LocalDate date = dpEndDate.getValue();
        if (date == null && !isBlank(txtEndTime)) {
            showValidationMessage(errEndDate, "End date is required when time is set.");
            return false;
        }
        hideValidationMessage(errEndDate);
        return true;
    }

    private boolean validateEndTime() {
        String value = txtEndTime.getText();
        if ((value == null || value.isBlank()) && dpEndDate.getValue() != null) {
            showValidationMessage(errEndTime, "End time is required when date is set.");
            return false;
        }
        if (value != null && !value.isBlank() && !isValidTime(value)) {
            showValidationMessage(errEndTime, "Use HH:mm format.");
            return false;
        }
        if (!isEndRangeValid()) {
            showValidationMessage(errEndTime, "End date and time must be after the start date and time.");
            return false;
        }
        hideValidationMessage(errEndTime);
        return true;
    }

    private boolean validateDateRange() {
        if (!hasEndInput()) {
            if (errEndTime != null && "End date and time must be after the start date and time.".equals(errEndTime.getText())) {
                hideValidationMessage(errEndTime);
            }
            return true;
        }
        if (!validateStartDate() || !validateStartTime() || !validateEndDate() || !validateEndTime()) {
            return false;
        }
        if (!isEndRangeValid()) {
            showValidationMessage(errEndTime, "End date and time must be after the start date and time.");
            return false;
        }
        if ("End date and time must be after the start date and time.".equals(errEndTime.getText())) {
            hideValidationMessage(errEndTime);
        }
        return true;
    }

    private boolean validateTicketTypes() {
        int activeTicketTypes = EventValidationRules.countActiveTicketTypes(ticketTypes);
        if (activeTicketTypes == 0) {
            showValidationMessage(errTicketTypes, "At least one active ticket type is required.");
            return false;
        }

        for (TicketCategory ticketType : ticketTypes) {
            if (ticketType == null || ticketType.isDeleted()) {
                continue;
            }
            Integer seats = ticketType.getSeatCount();
            if (seats == null || seats < EventValidationRules.MIN_SEAT_COUNT) {
                showValidationMessage(errTicketTypes, "Every active ticket type must have at least "
                        + EventValidationRules.MIN_SEAT_COUNT + " seat.");
                return false;
            }
        }

        hideValidationMessage(errTicketTypes);
        return true;
    }

    private List<RefundImpact> buildRefundImpacts() {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            return List.of();
        }

        List<RefundImpact> impacts = new ArrayList<>();
        for (TicketCategory original : originalTicketTypes) {
            if (original == null || original.getId() == null || original.getId() <= 0) {
                continue;
            }

            int soldCount = safeCount(original.getSoldCount());
            if (soldCount <= 0) {
                continue;
            }

            TicketCategory updated = findTicketTypeById(ticketTypes, original.getId());
            if (updated == null || updated.isDeleted()) {
                impacts.add(new RefundImpact(ticketTypeName(original), soldCount));
                continue;
            }

            int updatedSeatCount = safeSeatCount(updated);
            if (updatedSeatCount < soldCount) {
                impacts.add(new RefundImpact(ticketTypeName(updated), soldCount - updatedSeatCount));
            }
        }
        return impacts;
    }

    private boolean confirmRefundImpacts(List<RefundImpact> impacts) {
        if (impacts == null || impacts.isEmpty()) {
            return true;
        }

        StringBuilder message = new StringBuilder();
        if (impacts.size() == 1) {
            RefundImpact impact = impacts.get(0);
            message.append(impact.refundCount())
                    .append(" ")
                    .append(impact.ticketTypeName())
                    .append(impact.refundCount() == 1 ? " ticket will be refunded if you continue." : " tickets will be refunded if you continue.");
        } else {
            message.append("The following tickets will be refunded if you continue:\n\n");
            for (RefundImpact impact : impacts) {
                message.append(impact.refundCount())
                        .append(" ")
                        .append(impact.ticketTypeName())
                        .append(impact.refundCount() == 1 ? " ticket" : " tickets")
                        .append("\n");
            }
        }

        return DialogUtils.showConfirmation(
                "Save Event Changes",
                "Refund affected tickets?",
                message.toString().trim(),
                "Continue",
                txtName.getScene().getWindow()
        );
    }

    private void updateTicketTypeSummary() {
        if (lblTotalSeats != null) {
            lblTotalSeats.setText("Total seats: " + EventValidationRules.countActiveSeats(ticketTypes));
        }
        if (lblTotalSold != null) {
            lblTotalSold.setText("Total sold: " + countActiveSold(ticketTypes));
        }
    }

    private int countActiveSold(List<TicketCategory> categories) {
        int total = 0;
        if (categories == null) {
            return total;
        }
        for (TicketCategory category : categories) {
            if (category == null || category.isDeleted()) {
                continue;
            }
            total += safeCount(category.getSoldCount());
        }
        return total;
    }

    private TicketCategory findTicketTypeById(List<TicketCategory> categories, Long id) {
        if (categories == null || id == null) {
            return null;
        }
        for (TicketCategory category : categories) {
            if (category != null && id.equals(category.getId())) {
                return category;
            }
        }
        return null;
    }

    private String ticketTypeName(TicketCategory category) {
        String normalized = EventValidationRules.normalizeRequired(category == null ? null : category.getName());
        return normalized.isEmpty() ? "selected ticket type" : normalized;
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeSeatCount(TicketCategory category) {
        Integer seatCount = category == null ? null : category.getSeatCount();
        return seatCount == null ? 0 : seatCount;
    }

    private boolean isValidTime(String value) {
        try {
            LocalTime.parse(value.trim(), TIME_FORMAT);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private boolean isEndRangeValid() {
        LocalDateTime start = parseDateTime(dpStartDate.getValue(), txtStartTime.getText());
        LocalDateTime end = parseDateTime(dpEndDate.getValue(), txtEndTime.getText());
        return start == null || end == null || end.isAfter(start);
    }

    private LocalDateTime parseDateTime(LocalDate date, String timeText) {
        if (date == null || timeText == null || timeText.isBlank() || !isValidTime(timeText)) {
            return null;
        }
        LocalTime time = LocalTime.parse(timeText.trim(), TIME_FORMAT);
        return date.atTime(time);
    }

    private boolean isBlank(TextField field) {
        String value = field.getText();
        return value == null || value.trim().isEmpty();
    }

    private boolean hasEndInput() {
        return dpEndDate.getValue() != null || !isBlank(txtEndTime);
    }

    private void updateTicketTypeFilter() {
        visibleTicketTypes.setPredicate(category -> showDeletedTicketTypes || !category.isDeleted());
        btnToggleDeletedTicketTypes.setText(showDeletedTicketTypes
                ? "Hide Deleted Types"
                : "Show Deleted Types");
        updateTicketTypeButtonState(ticketTypesTable.getSelectionModel().getSelectedItem());
    }

    private void updateTicketTypeButtonState(TicketCategory selected) {
        boolean disable = selected == null;
        btnEditTicketType.setDisable(disable || selected.isDeleted());
        btnDeleteTicketType.setDisable(disable);
        if (disable) {
            btnDeleteTicketType.setText("Delete");
        } else {
            btnDeleteTicketType.setText(selected.isDeleted() ? "Restore" : "Delete");
        }
    }

    private void clearValidationMessages() {
        hideValidationMessage(errName);
        hideValidationMessage(errLocation);
        hideValidationMessage(errStartDate);
        hideValidationMessage(errStartTime);
        hideValidationMessage(errEndDate);
        hideValidationMessage(errEndTime);
        hideValidationMessage(errTicketTypes);
    }

    private void showValidationMessage(Label label, String message) {
        if (label == null) {
            return;
        }
        label.setText(message);
        label.setVisible(validationFeedbackEnabled);
        label.setManaged(validationFeedbackEnabled);
    }

    private void hideValidationMessage(Label label) {
        if (label == null) {
            return;
        }
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private record RefundImpact(String ticketTypeName, int refundCount) {
    }
}
