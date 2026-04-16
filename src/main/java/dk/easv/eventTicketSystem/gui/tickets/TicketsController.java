package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.model.AppModel;
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
    private Button deleteButton;
    @FXML
    private Button redeemButton;
    @FXML
    private Button printButton;
    @FXML
    private Button showDeletedButton;

    private final TicketPrintService ticketPrintService = new TicketPrintService();
    private final ListChangeListener<Ticket> ticketsListener = change -> {
        updateShowDeletedButtonText();
        restoreSelection();
    };

    private ObservableList<Ticket> observedTickets;
    private AppModel model;
    private StatusBanner statusBanner;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);

        colCode.setCellValueFactory(cd -> cd.getValue().codeProperty());
        colCustomerName.setCellValueFactory(cd -> cd.getValue().customerNameProperty());
        colEventName.setCellValueFactory(cd -> cd.getValue().eventNameProperty());
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatusLabel()));

        ticketsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldTicket, newTicket) -> {
            if (model != null && newTicket != null) {
                model.setSelectedTicket(newTicket);
            }
            updateActionState(newTicket);
        });

        ticketsTable.setRowFactory(table -> {
            var row = new javafx.scene.control.TableRow<Ticket>();
            row.setOnMouseClicked(event -> {
                Ticket item = row.getItem();
                if (item != null && model != null) {
                    model.setSelectedTicket(item);
                    updateActionState(item);
                }
            });
            return row;
        });

        ticketsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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

        updateShowDeletedButtonText();
        reloadAllTickets();
    }

    @FXML
    private void onAddTicket() {
        Event selectedEvent = resolveCurrentEvent();
        if (selectedEvent == null || selectedEvent.getId() == null || selectedEvent.getId() <= 0) {
            DialogUtils.showWarning("Add Ticket", null, "Please select an event first.");
            return;
        }

        Optional<TicketDialogController.TicketDraft> result = showTicketDialog(selectedEvent);
        result.ifPresent(draft -> {
            statusBanner.showSaving();

            Task<Ticket> task = new Task<>() {
                @Override
                protected Ticket call() throws Exception {
                    return model.addTicket(
                            selectedEvent,
                            draft.ticketCategoryId(),
                            draft.customerName(),
                            draft.customerEmail(),
                            draft.code()
                    );
                }
            };

            task.setOnSucceeded(workerStateEvent -> {
                statusBanner.showSaved();
                reloadAllTickets();
            });

            task.setOnFailed(workerStateEvent -> {
                statusBanner.showFailed();
                DialogUtils.showError("Add Ticket", null,
                        task.getException() == null ? "Unable to add ticket." : task.getException().getMessage());
            });

            new Thread(task, "add-ticket-task").start();
        });
    }

    private Optional<TicketDialogController.TicketDraft> showTicketDialog(Event selectedEvent) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            TicketDialogController controller = loader.getController();
            controller.setEvent(selectedEvent);

            Stage stage = new Stage();
            stage.setTitle("Add Ticket");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (ticketsTable != null && ticketsTable.getScene() != null) {
                stage.initOwner(ticketsTable.getScene().getWindow());
            }
            stage.setScene(new Scene(root));
            stage.setResizable(true);
            stage.showAndWait();

            if (controller.isSaved()) {
                return Optional.ofNullable(controller.getDraft());
            }
        } catch (IOException e) {
            DialogUtils.showError("Ticket Dialog", null, "Unable to open ticket dialog.");
        }
        return Optional.empty();
    }

    @FXML
    private void onDeleteTicket() {
        Ticket selected = ticketsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Delete Ticket", null, "Please select a ticket first.");
            return;
        }

        boolean shouldDelete = !selected.isDeleted();
        ActionDialogType dialogType = shouldDelete ? ActionDialogType.TICKET_DELETE : ActionDialogType.TICKET_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getCode(), resolveOwnerWindow())) {
            return;
        }

        statusBanner.showSaving();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.setTicketDeleted(selected, shouldDelete);
                return null;
            }
        };

        task.setOnSucceeded(workerStateEvent -> {
            statusBanner.showSaved();
            updateActionState(selected);
            reloadAllTickets();
        });

        task.setOnFailed(workerStateEvent -> {
            statusBanner.showFailed();
            DialogUtils.showError(dialogType.getWindowTitle(), null,
                    task.getException() == null ? "Unable to update ticket state." : task.getException().getMessage());
        });

        new Thread(task, (shouldDelete ? "delete" : "restore") + "-ticket-task").start();
    }

    @FXML
    private void onRedeemTicket() {
        Ticket selected = ticketsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Redeem Ticket", null, "Please select a ticket to redeem.");
            return;
        }
        if (selected.isDeleted()) {
            DialogUtils.showWarning("Redeem Ticket", null, "Deleted tickets cannot be redeemed.");
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
                reloadAllTickets();
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

        Optional<TicketPrintDialogController.TicketAction> action = showPrintTicketDialog(selected);
        if (action.isEmpty()) {
            return;
        }

        statusBanner.showPreparingTicket();
        Event selectedEvent = resolveCurrentEvent();

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

    private Optional<TicketPrintDialogController.TicketAction> showPrintTicketDialog(Ticket ticket) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewType.TICKET_PRINT_DIALOG.getFxmlPath()));
        try {
            Parent root = loader.load();
            TicketPrintDialogController controller = loader.getController();
            controller.setTicket(ticket);

            Stage stage = new Stage();
            stage.setTitle("Print Ticket");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (ticketsTable != null && ticketsTable.getScene() != null) {
                stage.initOwner(ticketsTable.getScene().getWindow());
            }
            stage.setScene(new Scene(root));
            stage.setResizable(false);
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

    private void reloadAllTickets() {
        ticketsTable.getSelectionModel().clearSelection();
        if (model != null) {
            model.tickets().clear();
        }
        updateActionState(null);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.loadAllTickets();
                return null;
            }
        };

        task.setOnFailed(workerStateEvent -> DialogUtils.showError("Load Tickets", null,
                task.getException() == null ? "Unable to load tickets." : task.getException().getMessage()));

        new Thread(task, "load-tickets-task").start();
    }

    private void restoreSelection() {
        if (model == null || ticketsTable == null) {
            return;
        }

        Ticket selected = model.getSelectedTicket();
        if (selected == null || selected.getId() == null) {
            return;
        }

        for (Ticket ticket : model.ticketsView()) {
            if (ticket != null && selected.getId().equals(ticket.getId())) {
                ticketsTable.getSelectionModel().select(ticket);
                ticketsTable.scrollTo(ticket);
                updateActionState(ticket);
                return;
            }
        }

        ticketsTable.getSelectionModel().clearSelection();
        updateActionState(null);
    }

    private void updateActionState(Ticket selected) {
        boolean hasSelection = selected != null;
        deleteButton.setDisable(!hasSelection);
        if (hasSelection) {
            deleteButton.setText(selected.isDeleted() ? "Restore Ticket" : "Delete Ticket");
        } else {
            deleteButton.setText("Delete Ticket");
        }
        redeemButton.setDisable(!hasSelection || selected.isRedeemed() || selected.isDeleted());
        printButton.setDisable(!hasSelection);
    }

    @FXML
    private void onToggleShowDeletedTickets() {
        if (model == null) {
            return;
        }
        model.setShowDeletedTickets(!model.isShowDeletedTickets());
        updateShowDeletedButtonText();
        reloadAllTickets();
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedTickets() ? "Hide Deleted Tickets" : "Show Deleted Tickets");
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
            stage.setResizable(false);
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
}
