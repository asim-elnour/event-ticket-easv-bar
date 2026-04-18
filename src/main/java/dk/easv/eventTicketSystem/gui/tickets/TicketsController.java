package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.DataViewMode;
import dk.easv.eventTicketSystem.gui.model.SearchScope;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.StatusBanner;
import dk.easv.eventTicketSystem.util.TicketPrintService;
import dk.easv.eventTicketSystem.util.ViewType;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class TicketsController implements ModelAware {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Ticket> ticketsTable;
    @FXML
    private TableColumn<Ticket, String> colCode;
    @FXML
    private TableColumn<Ticket, String> colCustomerName;
    @FXML
    private TableColumn<Ticket, String> colEventName;
    @FXML
    private TableColumn<Ticket, String> colStatus;
    @FXML
    private Button addButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button redeemButton;
    @FXML
    private Button printButton;
    @FXML
    private ChoiceBox<DataViewMode> viewChoice;

    private final TicketPrintService ticketPrintService = new TicketPrintService();
    private final Label placeholderLabel = new Label("No tickets found.");
    private final ListChangeListener<Ticket> ticketsListener = change -> {
        updatePlaceholder();
        restoreSelection();
    };

    private ObservableList<Ticket> observedTickets;
    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;
    private boolean suppressSelectionEvents;
    private boolean reloadPending = true;
    private String lastLoadKey;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        placeholderLabel.getStyleClass().add("muted-text");

        colCode.setCellValueFactory(cd -> cd.getValue().codeProperty());
        colCustomerName.setCellValueFactory(cd -> cd.getValue().customerNameProperty());
        colEventName.setCellValueFactory(cd -> cd.getValue().eventNameProperty());
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatusLabel()));

        ticketsTable.setPlaceholder(placeholderLabel);
        ticketsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldTicket, newTicket) -> {
            if (suppressSelectionEvents) {
                return;
            }
            if (model != null) {
                model.setSelectedTicket(newTicket);
            }
            updateActionState(newTicket);
        });

        ticketsTable.setRowFactory(table -> {
            var row = new javafx.scene.control.TableRow<Ticket>();
            row.setOnMouseClicked(event -> {
                Ticket item = row.getItem();
                if (model != null) {
                    model.setSelectedTicket(item);
                }
                updateActionState(item);
            });
            return row;
        });

        viewChoice.getItems().setAll(DataViewMode.values());
        viewChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (model == null || newValue == null || newValue == model.getTicketsViewMode()) {
                return;
            }
            model.setTicketsViewMode(newValue);
            requestReload(true);
        });

        ticketsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        updatePlaceholder();
        updateActionState(null);

        if (model != null) {
            bindModel();
        }
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        if (ticketsTable == null) {
            return;
        }
        bindModel();
    }

    private void bindModel() {
        if (model == null) {
            return;
        }

        if (observedTickets != null) {
            observedTickets.removeListener(ticketsListener);
        }

        var ticketsView = model.ticketsView();
        observedTickets = ticketsView;
        ticketsTable.setItems(ticketsView);
        ticketsView.comparatorProperty().bind(ticketsTable.comparatorProperty());
        observedTickets.addListener(ticketsListener);

        if (!modelListenersBound) {
            model.selectedEventProperty().addListener((obs, oldValue, newValue) -> {
                if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT) {
                    requestReload(false);
                }
                updateActionState(ticketsTable.getSelectionModel().getSelectedItem());
            });
            model.currentEventIdProperty().addListener((obs, oldValue, newValue) -> {
                if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT) {
                    requestReload(false);
                }
                updateActionState(ticketsTable.getSelectionModel().getSelectedItem());
            });
            model.ticketsViewModeProperty().addListener((obs, oldValue, newValue) -> {
                if (viewChoice.getValue() != newValue) {
                    viewChoice.setValue(newValue);
                }
                updatePlaceholder();
                updateActionState(ticketsTable.getSelectionModel().getSelectedItem());
                requestReload(false);
            });
            model.activeSearchScopeProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue == SearchScope.TICKETS) {
                    requestReload(false);
                }
            });
            modelListenersBound = true;
        }

        if (viewChoice.getValue() != model.getTicketsViewMode()) {
            viewChoice.setValue(model.getTicketsViewMode());
        }

        updatePlaceholder();
        requestReload(false);
    }

    @FXML
    private void onAddTicket() {
        Event selectedEvent = resolveCurrentEvent();
        if (selectedEvent == null || selectedEvent.getId() == null || selectedEvent.getId() <= 0) {
            DialogUtils.showWarning("Add Ticket", null, "Please select an event first.");
            return;
        }
        if (model == null || model.getTicketsViewMode() != DataViewMode.SELECTED_EVENT) {
            DialogUtils.showWarning("Add Ticket", null, "Switch to Selected Event view before adding tickets.");
            return;
        }
        if (selectedEvent.isDeleted()) {
            DialogUtils.showWarning("Add Ticket", null, "Tickets cannot be added to a deleted event.");
            return;
        }

        if (showTicketDialog(selectedEvent)) {
            statusBanner.showSaved();
            requestReload(true);
            refreshEventData();
            refreshCustomerData();
        }
    }

    private boolean showTicketDialog(Event selectedEvent) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            TicketDialogController controller = loader.getController();
            controller.setModel(model);
            controller.setEvent(selectedEvent);

            Stage stage = new Stage();
            stage.setTitle("Add Ticket");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (ticketsTable != null && ticketsTable.getScene() != null) {
                stage.initOwner(ticketsTable.getScene().getWindow());
            }
            stage.setScene(new Scene(root));
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            return controller.isSaved();
        } catch (IOException e) {
            DialogUtils.showError("Ticket Dialog", null, "Unable to open ticket dialog.");
        }
        return false;
    }

    @FXML
    private void onRefundTicket() {
        Ticket selected = ticketsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Refund Ticket", null, "Please select a ticket first.");
            return;
        }
        if (selected.isRefunded()) {
            DialogUtils.showWarning("Refund Ticket", null, "This ticket is already refunded.");
            return;
        }
        if (selected.isRedeemed()) {
            DialogUtils.showWarning("Refund Ticket", null, "Redeemed tickets cannot be refunded.");
            return;
        }

        ActionDialogType dialogType = ActionDialogType.TICKET_REFUND;
        if (!DialogUtils.confirmAction(dialogType, selected.getCode(), resolveOwnerWindow())) {
            return;
        }

        statusBanner.showSaving();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.refundTicket(selected);
                return null;
            }
        };

        task.setOnSucceeded(workerStateEvent -> {
            statusBanner.showSaved();
            requestReload(true);
            refreshEventData();
        });

        task.setOnFailed(workerStateEvent -> {
            statusBanner.showFailed();
            DialogUtils.showError(dialogType.getWindowTitle(), null,
                    task.getException() == null ? "Unable to refund ticket." : task.getException().getMessage());
        });

        new Thread(task, "refund-ticket-task").start();
    }

    @FXML
    private void onRedeemTicket() {
        Ticket selected = ticketsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Redeem Ticket", null, "Please select a ticket to redeem.");
            return;
        }
        if (selected.isRefunded()) {
            DialogUtils.showWarning("Redeem Ticket", null, "Refunded tickets cannot be redeemed.");
            return;
        }
        if (selected.isRedeemed()) {
            DialogUtils.showWarning("Redeem Ticket", null, "This ticket is already redeemed.");
            return;
        }

        Optional<Ticket> result = showRedeemDialog(selected);
        result.ifPresent(ticket -> {
            statusBanner.showSaving();

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    model.redeemTicket(ticket);
                    return null;
                }
            };

            task.setOnSucceeded(workerStateEvent -> {
                statusBanner.showSaved();
                requestReload(true);
                refreshEventData();
            });

            task.setOnFailed(workerStateEvent -> {
                statusBanner.showFailed();
                DialogUtils.showError("Redeem Ticket", null,
                        task.getException() == null ? "Unable to redeem ticket." : task.getException().getMessage());
            });

            new Thread(task, "redeem-ticket-task").start();
        });
    }

    @FXML
    private void onPrintTicket() {
        Ticket selected = ticketsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Print Ticket", null, "Please select a ticket first.");
            return;
        }

        Event selectedEvent = resolveCurrentEvent();
        Optional<TicketPrintDialogController.TicketAction> action = showPrintTicketDialog(selected, selectedEvent);
        if (action.isEmpty()) {
            return;
        }

        statusBanner.showPreparingTicket();

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return ticketPrintService.createTicketPdf(selected, selectedEvent);
            }
        };

        task.setOnSucceeded(evt -> {
            Path pdfPath = task.getValue();
            try {
                if (action.get() == TicketPrintDialogController.TicketAction.EMAIL) {
                    ticketPrintService.openMailClient(selected, selectedEvent, pdfPath);
                } else {
                    ticketPrintService.openPdfInBrowser(pdfPath);
                }
                statusBanner.showSaved();
            } catch (IOException ex) {
                statusBanner.showFailed();
                DialogUtils.showError("Print Ticket", null, ex.getMessage());
            }
        });

        task.setOnFailed(evt -> {
            statusBanner.showFailed();
            DialogUtils.showError("Print Ticket", null,
                    task.getException() == null ? "Unable to generate ticket PDF." : task.getException().getMessage());
        });

        new Thread(task, "print-ticket-task").start();
    }

    private Optional<TicketPrintDialogController.TicketAction> showPrintTicketDialog(Ticket ticket, Event event) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_PRINT_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            TicketPrintDialogController controller = loader.getController();
            controller.setTicket(ticket, event);

            Stage stage = new Stage();
            stage.setTitle("Print Ticket");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (ticketsTable != null && ticketsTable.getScene() != null) {
                stage.initOwner(ticketsTable.getScene().getWindow());
            }
            stage.setScene(new Scene(root));
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            return Optional.ofNullable(controller.getSelectedAction());
        } catch (IOException e) {
            DialogUtils.showError("Print Ticket", null, "Unable to open print dialog.");
            return Optional.empty();
        }
    }

    private Event resolveCurrentEvent() {
        if (model == null) {
            return null;
        }

        Event selectedEvent = model.getSelectedEvent();
        if (selectedEvent != null) {
            return selectedEvent;
        }

        long eventId = model.getCurrentEventId();
        for (Event event : model.events()) {
            if (event != null && event.getId() != null && event.getId() == eventId) {
                return event;
            }
        }
        return null;
    }

    private void reloadTickets(String loadKey) {
        suppressSelectionEvents = true;
        ticketsTable.getSelectionModel().clearSelection();
        if (model != null) {
            model.tickets().clear();
            if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
                model.setSelectedTicket(null);
            }
        }
        suppressSelectionEvents = false;
        updatePlaceholder();
        updateActionState(null);
        reloadPending = false;
        lastLoadKey = loadKey;

        if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT) {
                    model.loadTicketsForEvent(model.getCurrentEventId());
                } else {
                    model.loadAllTickets();
                }
                return null;
            }
        };

        task.setOnFailed(workerStateEvent -> {
            reloadPending = true;
            DialogUtils.showError("Load Tickets", null,
                    task.getException() == null ? "Unable to load tickets." : task.getException().getMessage());
        });

        new Thread(task, "load-tickets-task").start();
    }

    private void restoreSelection() {
        if (model == null || ticketsTable == null) {
            return;
        }

        Ticket selected = model.getSelectedTicket();
        if (selected == null || selected.getId() == null) {
            updateActionState(null);
            return;
        }

        for (Ticket ticket : model.ticketsView()) {
            if (ticket != null && selected.getId().equals(ticket.getId())) {
                suppressSelectionEvents = true;
                ticketsTable.getSelectionModel().select(ticket);
                ticketsTable.scrollTo(ticket);
                suppressSelectionEvents = false;
                updateActionState(ticket);
                model.setSelectedTicket(ticket);
                return;
            }
        }

        suppressSelectionEvents = true;
        ticketsTable.getSelectionModel().clearSelection();
        suppressSelectionEvents = false;
        model.setSelectedTicket(null);
        updateActionState(null);
    }

    private void updateActionState(Ticket selected) {
        boolean hasSelection = selected != null;
        addButton.setDisable(!canAddTicket());
        deleteButton.setDisable(!hasSelection || selected.isRedeemed() || selected.isRefunded());
        deleteButton.setText("Refund Ticket");
        redeemButton.setDisable(!hasSelection || selected.isRedeemed() || selected.isRefunded());
        printButton.setDisable(!hasSelection);
    }

    private boolean canAddTicket() {
        if (model == null || model.getTicketsViewMode() != DataViewMode.SELECTED_EVENT) {
            return false;
        }
        Event selectedEvent = resolveCurrentEvent();
        return selectedEvent != null
                && selectedEvent.getId() != null
                && selectedEvent.getId() > 0
                && !selectedEvent.isDeleted();
    }

    private void updatePlaceholder() {
        if (placeholderLabel == null || model == null) {
            return;
        }
        if (model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
            placeholderLabel.setText("Select an event to view tickets.");
            return;
        }
        placeholderLabel.setText("No tickets found.");
    }

    private Optional<Ticket> showRedeemDialog(Ticket ticket) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_REDEEM_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            TicketRedeemDialogController controller = loader.getController();
            controller.setTicket(ticket);

            Stage stage = new Stage();
            stage.setTitle("Redeem Ticket");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (ticketsTable != null && ticketsTable.getScene() != null) {
                stage.initOwner(ticketsTable.getScene().getWindow());
            }
            stage.setScene(new Scene(root));
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                return Optional.ofNullable(controller.getTicket());
            }
        } catch (IOException e) {
            DialogUtils.showError("Redeem Ticket", null, "Unable to open redeem dialog.");
        }
        return Optional.empty();
    }

    private Stage resolveOwnerWindow() {
        return ticketsTable != null && ticketsTable.getScene() != null
                ? (Stage) ticketsTable.getScene().getWindow()
                : null;
    }

    private void requestReload(boolean force) {
        if (model == null || ticketsTable == null) {
            return;
        }

        String loadKey = buildLoadKey();
        if (!force && !reloadPending && loadKey.equals(lastLoadKey)) {
            return;
        }

        reloadTickets(loadKey);
    }

    private void refreshEventData() {
        if (model == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (model.isAdmin()) {
                    model.loadAllEvents();
                } else {
                    long coordinatorId = model.getCurrentCoordinatorId();
                    if (coordinatorId > 0) {
                        model.loadEventsForCoordinator(coordinatorId);
                    }
                }
                return null;
            }
        };

        Thread thread = new Thread(task, "refresh-events-after-ticket-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshCustomerData() {
        if (model == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                    model.loadCustomersForEvent(model.getCurrentEventId());
                } else {
                    model.loadAllCustomers();
                }
                return null;
            }
        };

        Thread thread = new Thread(task, "refresh-customers-after-ticket-task");
        thread.setDaemon(true);
        thread.start();
    }

    private String buildLoadKey() {
        if (model == null) {
            return "tickets:none";
        }
        long eventId = model.getTicketsViewMode() == DataViewMode.SELECTED_EVENT
                ? model.getCurrentEventId()
                : -1L;
        return model.getTicketsViewMode() + "|" + eventId;
    }
}
