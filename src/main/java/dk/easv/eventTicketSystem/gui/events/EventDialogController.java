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
    private TextField txtCapacity;
    @FXML
    private TableView<TicketCategory> ticketTypesTable;
    @FXML
    private TableColumn<TicketCategory, String> colTicketTypeName;
    @FXML
    private TableColumn<TicketCategory, BigDecimal> colTicketTypePrice;
    @FXML
    private TableColumn<TicketCategory, Integer> colTicketTypeSeats;
    @FXML
    private TableColumn<TicketCategory, String> colTicketTypeStatus;
    @FXML
    private Button btnToggleDeletedTicketTypes;
    @FXML
    private Button btnEditTicketType;
    @FXML
    private Button btnDeleteTicketType;
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
    @FXML
    private Label errCapacity;

    private final ObservableList<TicketCategory> ticketTypes = FXCollections.observableArrayList();
    private final FilteredList<TicketCategory> visibleTicketTypes = new FilteredList<>(ticketTypes, category -> true);
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());

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

            txtCapacity.setText(Integer.toString(event.getCapacity()));
            ticketTypes.setAll(loadTicketTypesForEvent(event));
            updateTicketTypeFilter();
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
        txtCapacity.setText(Integer.toString(EventValidationRules.MIN_CAPACITY));
        ticketTypes.clear();
        updateTicketTypeFilter();
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
        if (!canOpenTicketTypeDialog("Add Ticket Type")) {
            return;
        }

        TicketCategory draft = new TicketCategory();
        draft.setPrice(BigDecimal.ZERO);
        draft.setSeatCount(1);

        TicketCategory created = showTicketTypeDialog(draft, false);
        if (created != null) {
            created.setDeleted(false);
            if (!fitsWithinCapacity(created, null, "Add Ticket Type")) {
                return;
            }
            ticketTypes.add(created);
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
            if (!fitsWithinCapacity(edited, selected, "Edit Ticket Type")) {
                return;
            }
            selected.restoreFrom(edited);
            ticketTypesTable.refresh();
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
        if (!shouldDelete) {
            TicketCategory restored = selected.copy();
            restored.setDeleted(false);
            if (!fitsWithinCapacity(restored, selected, "Restore Ticket Type")) {
                return;
            }
        }

        ActionDialogType dialogType = shouldDelete
                ? ActionDialogType.TICKET_CATEGORY_DELETE
                : ActionDialogType.TICKET_CATEGORY_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getName(), txtName.getScene().getWindow())) {
            return;
        }

        selected.setDeleted(shouldDelete);
        ticketTypesTable.refresh();
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
        event.setCapacity(parseCapacity());
        event.setUpdatedAt(LocalDateTime.now());
        event.setTicketTypes(ticketTypes);
        saved = true;
        close();
    }

    private List<TicketCategory> loadTicketTypesForEvent(Event event) {
        return event == null ? List.of() : event.getTicketTypesCopy();
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
        txtCapacity.textProperty().addListener((obs, oldValue, newValue) -> {
            validateCapacity();
            validateTicketTypes();
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
        ok = validateCapacity() && ok;
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

    private boolean validateCapacity() {
        String rawValue = txtCapacity.getText();
        if (rawValue == null || rawValue.isBlank()) {
            showValidationMessage(errCapacity, "Capacity is required.");
            return false;
        }
        Integer capacity = parseCapacityOrNull();
        if (capacity == null) {
            showValidationMessage(errCapacity, "Capacity must be a whole number.");
            return false;
        }
        if (capacity < EventValidationRules.MIN_CAPACITY) {
            showValidationMessage(errCapacity, "Capacity must be at least " + EventValidationRules.MIN_CAPACITY + ".");
            return false;
        }
        hideValidationMessage(errCapacity);
        return true;
    }

    private int parseCapacity() {
        Integer capacity = parseCapacityOrNull();
        return capacity == null ? EventValidationRules.MIN_CAPACITY : capacity;
    }

    private Integer parseCapacityOrNull() {
        String value = txtCapacity.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean validateTicketTypes() {
        int activeTicketTypes = EventValidationRules.countActiveTicketTypes(ticketTypes);
        if (activeTicketTypes == 0) {
            showValidationMessage(errTicketTypes, "At least one active ticket type is required.");
            return false;
        }

        Integer capacity = parseCapacityOrNull();
        if (capacity == null || capacity < EventValidationRules.MIN_CAPACITY) {
            hideValidationMessage(errTicketTypes);
            return true;
        }

        int activeSeatCount = getActiveSeatCount();
        if (activeSeatCount != capacity) {
            showValidationMessage(errTicketTypes, "Ticket type seats must match capacity exactly (allocated "
                    + activeSeatCount + " / capacity " + capacity + ").");
            return false;
        }

        hideValidationMessage(errTicketTypes);
        return true;
    }

    private boolean canOpenTicketTypeDialog(String title) {
        Integer capacity = parseCapacityOrNull();
        if (capacity == null || capacity < EventValidationRules.MIN_CAPACITY) {
            DialogUtils.showWarning(title, null,
                    "Enter a valid capacity of at least " + EventValidationRules.MIN_CAPACITY + " before managing ticket types.");
            return false;
        }

        if (getRemainingSeats(null) <= 0) {
            DialogUtils.showWarning(title, null,
                    "No free seats are left. Increase capacity or adjust the existing ticket types first.");
            return false;
        }
        return true;
    }

    private boolean fitsWithinCapacity(TicketCategory candidate,
                                       TicketCategory currentVersion,
                                       String title) {
        Integer capacity = parseCapacityOrNull();
        if (capacity == null || capacity < EventValidationRules.MIN_CAPACITY) {
            DialogUtils.showWarning(title, null,
                    "Enter a valid capacity of at least " + EventValidationRules.MIN_CAPACITY + " before managing ticket types.");
            return false;
        }

        int currentSeats = currentVersion != null && !currentVersion.isDeleted() ? safeSeatCount(currentVersion) : 0;
        int candidateSeats = candidate != null && !candidate.isDeleted() ? safeSeatCount(candidate) : 0;
        int projectedSeats = getActiveSeatCount() - currentSeats + candidateSeats;
        if (projectedSeats > capacity) {
            DialogUtils.showWarning(title, null,
                    "This ticket type would exceed event capacity. Allocated seats cannot go above " + capacity + ".");
            return false;
        }
        return true;
    }

    private int getActiveSeatCount() {
        return EventValidationRules.countActiveSeats(ticketTypes);
    }

    private int getRemainingSeats(TicketCategory categoryToIgnore) {
        Integer capacity = parseCapacityOrNull();
        if (capacity == null) {
            return 0;
        }

        int activeSeats = getActiveSeatCount();
        if (categoryToIgnore != null && !categoryToIgnore.isDeleted()) {
            activeSeats -= safeSeatCount(categoryToIgnore);
        }
        return capacity - activeSeats;
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
        hideValidationMessage(errCapacity);
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
}
