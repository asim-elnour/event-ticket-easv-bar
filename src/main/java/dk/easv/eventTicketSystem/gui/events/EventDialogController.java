package dk.easv.eventTicketSystem.gui.events;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.util.DialogUtils;
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
        ticketTypesTable.setMinHeight(220);
        ticketTypesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ticketTypesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                updateTicketTypeButtonState(newValue));
        btnEditTicketType.setDisable(true);
        btnDeleteTicketType.setDisable(true);
        updateTicketTypeButtonState(null);
        updateTicketTypeFilter();

        setupLiveValidation();
    }

    public void setCoordinatorId(long coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    public void setEvent(Event event) {
        this.event = event;
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
        txtCapacity.setText("0");
        ticketTypes.clear();
        updateTicketTypeFilter();
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
        draft.setSeatCount(1);

        TicketCategory created = showTicketTypeDialog(draft, false);
        if (created != null) {
            created.setDeleted(false);
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

        event.setName(txtName.getText().trim());
        event.setLocation(txtLocation.getText().trim());
        event.setLocationGuidance(normalizeText(txtGuidance.getText()));
        event.setNotes(normalizeText(txtNotes.getText()));
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
        dpStartDate.valueProperty().addListener((obs, oldValue, newValue) -> validateStartDate());
        txtStartTime.textProperty().addListener((obs, oldValue, newValue) -> validateStartTime());
        dpEndDate.valueProperty().addListener((obs, oldValue, newValue) -> validateEndDate());
        txtEndTime.textProperty().addListener((obs, oldValue, newValue) -> validateEndTime());
        txtCapacity.textProperty().addListener((obs, oldValue, newValue) -> validateCapacity());
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
        ok = validateCapacity() && ok;
        ok = validateTicketTypes() && ok;
        return ok;
    }

    private boolean validateName() {
        String value = txtName.getText();
        if (value == null || value.isBlank()) {
            errName.setText("Name is required.");
            errName.setVisible(true);
            return false;
        }
        if (value.length() > 255) {
            errName.setText("Name too long (max 255).");
            errName.setVisible(true);
            return false;
        }
        errName.setVisible(false);
        return true;
    }

    private boolean validateLocation() {
        String value = txtLocation.getText();
        if (value == null || value.isBlank()) {
            errLocation.setText("Location is required.");
            errLocation.setVisible(true);
            return false;
        }
        if (value.length() > 255) {
            errLocation.setText("Location too long (max 255).");
            errLocation.setVisible(true);
            return false;
        }
        errLocation.setVisible(false);
        return true;
    }

    private boolean validateStartDate() {
        if (dpStartDate.getValue() == null) {
            errStartDate.setText("Start date is required.");
            errStartDate.setVisible(true);
            return false;
        }
        errStartDate.setVisible(false);
        return true;
    }

    private boolean validateStartTime() {
        String value = txtStartTime.getText();
        if (value == null || value.isBlank()) {
            errStartTime.setText("Start time is required.");
            errStartTime.setVisible(true);
            return false;
        }
        if (!isValidTime(value)) {
            errStartTime.setText("Use HH:mm format.");
            errStartTime.setVisible(true);
            return false;
        }
        errStartTime.setVisible(false);
        return true;
    }

    private boolean validateEndDate() {
        LocalDate date = dpEndDate.getValue();
        if (date == null && !isBlank(txtEndTime)) {
            errEndDate.setText("End date is required when time is set.");
            errEndDate.setVisible(true);
            return false;
        }
        errEndDate.setVisible(false);
        return true;
    }

    private boolean validateEndTime() {
        String value = txtEndTime.getText();
        if ((value == null || value.isBlank()) && dpEndDate.getValue() != null) {
            errEndTime.setText("End time is required when date is set.");
            errEndTime.setVisible(true);
            return false;
        }
        if (value != null && !value.isBlank() && !isValidTime(value)) {
            errEndTime.setText("Use HH:mm format.");
            errEndTime.setVisible(true);
            return false;
        }
        errEndTime.setVisible(false);
        return true;
    }

    private boolean validateCapacity() {
        String value = txtCapacity.getText();
        if (value == null || value.isBlank()) {
            errCapacity.setText("Capacity is required.");
            errCapacity.setVisible(true);
            return false;
        }
        try {
            int capacity = Integer.parseInt(value.trim());
            if (capacity < 0) {
                errCapacity.setText("Capacity must be zero or higher.");
                errCapacity.setVisible(true);
                return false;
            }
        } catch (NumberFormatException ex) {
            errCapacity.setText("Capacity must be a whole number.");
            errCapacity.setVisible(true);
            return false;
        }
        errCapacity.setVisible(false);
        return true;
    }

    private int parseCapacity() {
        try {
            return Integer.parseInt(txtCapacity.getText().trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean validateTicketTypes() {
        boolean hasActive = ticketTypes.stream().anyMatch(category -> !category.isDeleted());
        if (!hasActive) {
            errTicketTypes.setText("At least one ticket type is required.");
            errTicketTypes.setVisible(true);
            return false;
        }

        errTicketTypes.setVisible(false);
        return true;
    }

    private boolean isValidTime(String value) {
        try {
            LocalTime.parse(value.trim(), TIME_FORMAT);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private LocalDateTime parseDateTime(LocalDate date, String timeText) {
        if (date == null || timeText == null || timeText.isBlank()) {
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
}
