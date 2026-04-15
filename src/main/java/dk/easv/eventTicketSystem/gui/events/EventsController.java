package dk.easv.eventTicketSystem.gui.events;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.EventUiText;
import dk.easv.eventTicketSystem.util.StatusBanner;
import dk.easv.eventTicketSystem.util.ViewType;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class EventsController implements ModelAware {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML
    private Label sectionTitleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Event> eventsTable;
    @FXML
    private TableColumn<Event, String> colName;
    @FXML
    private TableColumn<Event, String> colLocation;
    @FXML
    private TableColumn<Event, LocalDateTime> colStart;
    @FXML
    private TableColumn<Event, String> colStatus;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button showDeletedButton;

    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        initializeStaticTexts();

        colName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colLocation.setCellValueFactory(cd -> cd.getValue().locationProperty());
        colStart.setCellValueFactory(cd -> cd.getValue().startTimeProperty());
        colStatus.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(EventUiText.statusLabel(cd.getValue())));

        colStart.setCellFactory(col -> new DateTimeCell());

        eventsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldEvent, newEvent) -> {
            if (model != null) {
                model.setSelectedEvent(newEvent);
                if (newEvent != null) {
                    model.setCurrentEventId(newEvent.getId());
                } else {
                    model.setCurrentEventId(0L);
                }
            }
            updateActionState(newEvent);
        });

        eventsTable.setRowFactory(table -> {
            var row = new javafx.scene.control.TableRow<Event>();
            row.setOnMouseClicked(event -> {
                Event item = row.getItem();
                if (item != null && model != null) {
                    model.setSelectedEvent(item);
                    model.setCurrentEventId(item.getId());
                    updateActionState(item);
                }
            });
            return row;
        });

        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        updateActionState(null);

        if (model != null) {
            bindModel();
        }
    }

    private void initializeStaticTexts() {
        sectionTitleLabel.setText("Events");
        colName.setText("Name");
        colLocation.setText("Location");
        colStart.setText("Start");
        colStatus.setText("Status");
        addButton.setText("Add Event");
        editButton.setText("Edit Event");
        deleteButton.setText("Delete Event");
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        if (eventsTable == null) {
            return;
        }
        bindModel();
    }

    private void bindModel() {
        eventsTable.setItems(model.eventsView());
        model.eventsView().comparatorProperty().bind(eventsTable.comparatorProperty());
        updateShowDeletedButtonText();

        eventsTable.getSelectionModel().clearSelection();
        model.setSelectedEvent(null);
        model.setCurrentEventId(0L);

        if (!modelListenersBound) {
            model.currentCoordinatorIdProperty().addListener((obs, oldValue, newValue) -> {
                if (model.isAdmin()) {
                    return;
                }
                if (newValue != null && newValue > 0) {
                    reloadEvents(newValue);
                }
            });

            model.eventsView().addListener((ListChangeListener<Event>) change -> ensureEventSelection());
            modelListenersBound = true;
        }

        if (model.isAdmin()) {
            reloadAllEvents();
        } else {
            long coordinatorId = model.getCurrentCoordinatorId();
            if (coordinatorId > 0) {
                reloadEvents(coordinatorId);
            }
        }
    }

    @FXML
    private void onToggleShowDeletedEvents() {
        if (model == null) {
            return;
        }

        model.setShowDeletedEvents(!model.isShowDeletedEvents());
        updateShowDeletedButtonText();
        reloadCurrentEventView();
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedEvents()
                ? "Hide Deleted Events"
                : "Show Deleted Events");
    }

    @FXML
    private void onAddEvent() {
        Optional<Event> result = showEventDialog(null);
        result.ifPresent(event -> {
            statusBanner.showSaving();

            Task<Event> task = new Task<>() {
                @Override
                protected Event call() throws Exception {
                    return model.addEvent(event);
                }
            };

            task.setOnSucceeded(workerStateEvent -> {
                statusBanner.showSaved();
                reloadCurrentEventView();
            });

            task.setOnFailed(workerStateEvent -> {
                statusBanner.showFailed();
                showEventActionErrorDialog("Add Event Failed", "We couldn't add this event right now.", task.getException());
            });

            new Thread(task, "add-event-task").start();
        });
    }

    @FXML
    private void onEditEvent() {
        Event selected = eventsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Edit Event", null, "Please select an event to edit.");
            return;
        }

        Optional<Event> result = showEventDialog(selected);
        result.ifPresent(event -> {
            if (!DialogUtils.confirmAction(ActionDialogType.EVENT_EDIT, event.getName(), resolveOwnerWindow())) {
                return;
            }

            statusBanner.showSaving();

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    model.updateEvent(event);
                    return null;
                }
            };

            task.setOnSucceeded(workerStateEvent -> {
                statusBanner.showSaved();
                updateActionState(selected);
                reloadCurrentEventView();
            });

            task.setOnFailed(workerStateEvent -> {
                statusBanner.showFailed();
                showEventActionErrorDialog("Edit Event Failed", "We couldn't save changes for this event.", task.getException());
            });

            new Thread(task, "edit-event-task").start();
        });
    }

    @FXML
    private void onDeleteEvent() {
        Event selected = eventsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Delete Event", null, "Please select an event to delete.");
            return;
        }

        boolean shouldDelete = !selected.isDeleted();
        ActionDialogType dialogType = shouldDelete ? ActionDialogType.EVENT_DELETE : ActionDialogType.EVENT_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getName(), resolveOwnerWindow())) {
            return;
        }

        statusBanner.showSaving();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.setEventDeleted(selected, model.getCurrentCoordinatorId(), shouldDelete);
                return null;
            }
        };

        task.setOnSucceeded(workerStateEvent -> {
            statusBanner.showSaved();
            reloadCurrentEventView();
        });

        task.setOnFailed(workerStateEvent -> {
            statusBanner.showFailed();
            showEventActionErrorDialog("Event Update Failed", "We couldn't update this event right now.", task.getException());
        });

        new Thread(task, (shouldDelete ? "delete" : "restore") + "-event-task").start();
    }

    private void reloadCurrentEventView() {
        if (model == null) {
            return;
        }
        if (model.isAdmin()) {
            reloadAllEvents();
            return;
        }

        long coordinatorId = model.getCurrentCoordinatorId();
        if (coordinatorId > 0) {
            reloadEvents(coordinatorId);
        }
    }

    private void reloadEvents(long coordinatorId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.loadEventsForCoordinator(coordinatorId);
                return null;
            }
        };

        task.setOnFailed(workerStateEvent -> DialogUtils.showError("Load Events", null,
                task.getException() == null ? "Unable to load events." : task.getException().getMessage()));

        new Thread(task, "load-events-task").start();
    }

    private void reloadAllEvents() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.loadAllEvents();
                return null;
            }
        };

        task.setOnFailed(workerStateEvent -> DialogUtils.showError("Load Events", null,
                task.getException() == null ? "Unable to load events." : task.getException().getMessage()));

        new Thread(task, "load-all-events-task").start();
    }

    private void ensureEventSelection() {
        if (model == null || eventsTable == null) {
            return;
        }

        Event selected = eventsTable.getSelectionModel().getSelectedItem();
        if (selected != null && model.eventsView().contains(selected)) {
            updateActionState(selected);
            return;
        }

        Event preferred = pickPreferredEvent();
        if (preferred == null) {
            updateActionState(null);
            model.setSelectedEvent(null);
            model.setCurrentEventId(0L);
            return;
        }

        eventsTable.getSelectionModel().select(preferred);
        model.setSelectedEvent(preferred);
        model.setCurrentEventId(preferred.getId() == null ? 0L : preferred.getId());
        updateActionState(preferred);
    }

    private Event pickPreferredEvent() {
        for (Event event : model.eventsView()) {
            if (event != null && !event.isDeleted()) {
                return event;
            }
        }

        for (Event event : model.eventsView()) {
            if (event != null) {
                return event;
            }
        }

        return null;
    }

    private void updateActionState(Event selected) {
        if (selected == null) {
            editButton.setDisable(true);
            deleteButton.setDisable(true);
            deleteButton.setText("Delete Event");
            return;
        }

        boolean isDeleted = selected.isDeleted();
        editButton.setDisable(isDeleted);
        deleteButton.setDisable(false);
        deleteButton.setText(isDeleted ? "Restore Event" : "Delete Event");
    }

    private Optional<Event> showEventDialog(Event existing) {
        boolean isEdit = existing != null;
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.EVENT_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            EventDialogController controller = loader.getController();
            controller.setCoordinatorId(model.getCurrentCoordinatorId());
            controller.setEvent(existing);

            Stage stage = new Stage();
            stage.setTitle(isEdit ? "Edit Event" : "Add Event");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (eventsTable != null && eventsTable.getScene() != null) {
                stage.initOwner(eventsTable.getScene().getWindow());
            }
            Scene scene = new Scene(root);
            stage.setScene(scene);
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                return Optional.ofNullable(controller.getEvent());
            }
        } catch (IOException e) {
            DialogUtils.showError("Event Dialog", null, "Unable to open event dialog.");
        }
        return Optional.empty();
    }

    private record ErrorDialogDetails(String type, String error) {}

    private void showEventActionErrorDialog(String title, String message, Throwable throwable) {
        ErrorDialogDetails details = mapEventActionError(throwable);
        DialogUtils.showDetailedError(
                title,
                message,
                details.type(),
                details.error(),
                resolveOwnerWindow()
        );
    }

    private Window resolveOwnerWindow() {
        return eventsTable != null && eventsTable.getScene() != null
                ? eventsTable.getScene().getWindow()
                : null;
    }

    private ErrorDialogDetails mapEventActionError(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String technicalMessage = sanitizeMessage(root == null ? null : root.getMessage());
        if (technicalMessage == null) {
            technicalMessage = sanitizeMessage(throwable == null ? null : throwable.getMessage());
        }
        String normalized = technicalMessage == null ? "" : technicalMessage.toLowerCase(Locale.ROOT);

        String type;
        String detail;

        if (isDatabaseConnectionIssue(root, normalized)) {
            type = "Database Connection";
            detail = "Could not connect to the database.";
        } else if (throwable instanceof EventException eventException) {
            switch (eventException.getType()) {
                case VALIDATION_ERROR -> {
                    type = "Validation";
                    detail = "Some event data is invalid. Please review the form values.";
                }
                case NOT_FOUND -> {
                    type = "Event Not Found";
                    detail = "The selected event record no longer exists.";
                }
                case DATABASE_ERROR -> {
                    type = "Database Error";
                    detail = "The database could not process this request.";
                }
                case UNKNOWN -> {
                    type = "Unexpected Error";
                    detail = "Something unexpected happened while processing this request.";
                }
                default -> {
                    type = "Unexpected Error";
                    detail = "Something unexpected happened while processing this request.";
                }
            }
        } else if (containsAny(normalized, "permission", "denied", "forbidden", "not authorized", "unauthorized")) {
            type = "Permission";
            detail = "You do not have permission to perform this action.";
        } else if (root instanceof SQLException) {
            type = "Database Error";
            detail = "The database could not process this request.";
        } else {
            type = "Unexpected Error";
            detail = "Something unexpected happened while processing this request.";
        }

        return new ErrorDialogDetails(type, withTechnicalDetail(detail, technicalMessage));
    }

    private boolean isDatabaseConnectionIssue(Throwable root, String normalizedMessage) {
        if (containsAny(normalizedMessage, "connect", "connection", "timeout", "timed out", "socket", "network")) {
            return true;
        }
        if (!(root instanceof SQLException sqlException)) {
            return false;
        }
        String state = sqlException.getSQLState();
        return state != null && state.startsWith("08");
    }

    private String withTechnicalDetail(String friendlyMessage, String technicalMessage) {
        if (technicalMessage == null || technicalMessage.isBlank()) {
            return friendlyMessage;
        }
        return friendlyMessage + " (" + abbreviate(technicalMessage, 180) + ")";
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard < 20) {
            current = current.getCause();
            guard++;
        }
        return current;
    }

    private static class DateTimeCell extends TableCell<Event, LocalDateTime> {
        @Override
        protected void updateItem(LocalDateTime value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) {
                setText(null);
            } else {
                setText(DATE_TIME_FORMAT.format(value));
            }
        }
    }
}
