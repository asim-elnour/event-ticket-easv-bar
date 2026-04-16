package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

public class AppModel {

    private final EventModel eventModel = new EventModel();
    private final TicketModel ticketModel = new TicketModel();
    private final CustomerModel customerModel = new CustomerModel();
    private final UserModel userModel = new UserModel();
    private final SearchModel searchModel = new SearchModel();

    private final ObjectProperty<User> selectedUser = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Event> selectedEvent = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Ticket> selectedTicket = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Customer> selectedCustomer = new SimpleObjectProperty<>(null);
    private final ObjectProperty<User> selectedEventCoordinator = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Long> currentCoordinatorId = new SimpleObjectProperty<>(0L);
    private final ObjectProperty<Long> currentEventId = new SimpleObjectProperty<>(0L);
    private final ObjectProperty<DataViewMode> ticketsViewMode = new SimpleObjectProperty<>(DataViewMode.SELECTED_EVENT);
    private final ObjectProperty<DataViewMode> customersViewMode = new SimpleObjectProperty<>(DataViewMode.SELECTED_EVENT);
    private final ObjectProperty<DataViewMode> coordinatorViewMode = new SimpleObjectProperty<>(DataViewMode.SELECTED_EVENT);

    private final User currentUser;
    private final boolean admin;
    private final boolean coordinator;

    public AppModel() {
        currentUser = SessionManager.getCurrentUser();
        admin = currentUser != null &&
                currentUser.hasRole(Role.ADMIN);
        coordinator = currentUser != null &&
                currentUser.hasRole(Role.COORDINATOR);

        if (coordinator && currentUser.getId() != null) {
            currentCoordinatorId.set(currentUser.getId());
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isCoordinator() {
        return coordinator;
    }

    public UserModel users() {
        return userModel;
    }

    public EventModel eventsModel() {
        return eventModel;
    }

    public TicketModel ticketsModel() {
        return ticketModel;
    }

    public CustomerModel customersModel() {
        return customerModel;
    }

    public SearchModel search() {
        return searchModel;
    }

    public ObjectProperty<SearchScope> activeSearchScopeProperty() {
        return searchModel.activeScopeProperty();
    }

    public SearchScope getActiveSearchScope() {
        return searchModel.getActiveScope();
    }

    public void setActiveSearchScope(SearchScope scope) {
        searchModel.setActiveScope(scope);
    }

    public SearchModel.SearchState getSearchState(SearchScope scope) {
        return searchModel.getState(scope);
    }

    public ObservableList<User> adminAndCoordinatorUsers() {
        return userModel.adminAndCoordinatorUsers();
    }

    public SortedList<User> adminAndCoordinatorUsersView() {
        return userModel.adminAndCoordinatorUsersView();
    }

    public boolean isShowDeletedAdminAndCoordinatorUsers() {
        return userModel.isShowDeletedAdminAndCoordinatorUsers();
    }

    public void setShowDeletedAdminAndCoordinatorUsers(boolean showDeleted) {
        userModel.setShowDeletedAdminAndCoordinatorUsers(showDeleted);
    }

    public ObservableList<User> coordinatorUsers() {
        return userModel.coordinatorUsers();
    }

    public SortedList<User> coordinatorUsersView() {
        return userModel.coordinatorUsersView();
    }

    public boolean isShowDeletedCoordinatorUsers() {
        return userModel.isShowDeletedCoordinatorUsers();
    }

    public void setShowDeletedCoordinatorUsers(boolean showDeleted) {
        userModel.setShowDeletedCoordinatorUsers(showDeleted);
    }

    public ObservableList<Event> events() {
        return eventModel.events();
    }

    public SortedList<Event> eventsView() {
        return eventModel.eventsView();
    }

    public boolean isShowDeletedEvents() {
        return eventModel.isShowDeletedEvents();
    }

    public void setShowDeletedEvents(boolean showDeletedEvents) {
        eventModel.setShowDeletedEvents(showDeletedEvents);
    }


    public ObservableList<Ticket> tickets() {
        return ticketModel.tickets();
    }

    public SortedList<Ticket> ticketsView() {
        return ticketModel.ticketsView();
    }


    public boolean isShowDeletedTickets() {
        return ticketModel.isShowDeletedTickets();
    }

    public void setShowDeletedTickets(boolean showDeletedTickets) {
        ticketModel.setShowDeletedTickets(showDeletedTickets);
    }

    public ObservableList<Customer> customers() {
        return customerModel.customers();
    }

    public SortedList<Customer> customersView() {
        return customerModel.customersView();
    }

    public boolean isShowDeletedCustomerTickets() {
        return customerModel.isShowDeletedCustomerTickets();
    }

    public void setShowDeletedCustomerTickets(boolean showDeletedCustomerTickets) {
        customerModel.setShowDeletedCustomerTickets(showDeletedCustomerTickets);
    }

    public ObjectProperty<User> selectedUserProperty() {
        return selectedUser;
    }

    public User getSelectedUser() {
        return selectedUser.get();
    }

    public void setSelectedUser(User user) {
        selectedUser.set(user);
    }

    public ObjectProperty<Event> selectedEventProperty() {
        return selectedEvent;
    }

    public Event getSelectedEvent() {
        return selectedEvent.get();
    }

    public void setSelectedEvent(Event event) {
        selectedEvent.set(event);
    }

    public ObjectProperty<Ticket> selectedTicketProperty() {
        return selectedTicket;
    }

    public Ticket getSelectedTicket() {
        return selectedTicket.get();
    }

    public void setSelectedTicket(Ticket ticket) {
        selectedTicket.set(ticket);
    }

    public ObjectProperty<Customer> selectedCustomerProperty() {
        return selectedCustomer;
    }

    public Customer getSelectedCustomer() {
        return selectedCustomer.get();
    }

    public void setSelectedCustomer(Customer customer) {
        selectedCustomer.set(customer);
    }

    public ObjectProperty<User> selectedEventCoordinatorProperty() {
        return selectedEventCoordinator;
    }

    public User getSelectedEventCoordinator() {
        return selectedEventCoordinator.get();
    }

    public void setSelectedEventCoordinator(User coordinatorUser) {
        selectedEventCoordinator.set(coordinatorUser);
    }

    public ObjectProperty<Long> currentCoordinatorIdProperty() {
        return currentCoordinatorId;
    }

    public long getCurrentCoordinatorId() {
        return currentCoordinatorId.get();
    }

    public void setCurrentCoordinatorId(long coordinatorId) {
        currentCoordinatorId.set(coordinatorId);
    }

    public ObjectProperty<Long> currentEventIdProperty() {
        return currentEventId;
    }

    public long getCurrentEventId() {
        return currentEventId.get();
    }

    public void setCurrentEventId(long eventId) {
        currentEventId.set(eventId);
    }

    public ObjectProperty<DataViewMode> ticketsViewModeProperty() {
        return ticketsViewMode;
    }

    public DataViewMode getTicketsViewMode() {
        return normalizeViewMode(ticketsViewMode.get());
    }

    public void setTicketsViewMode(DataViewMode viewMode) {
        ticketsViewMode.set(normalizeViewMode(viewMode));
    }

    public ObjectProperty<DataViewMode> customersViewModeProperty() {
        return customersViewMode;
    }

    public DataViewMode getCustomersViewMode() {
        return normalizeViewMode(customersViewMode.get());
    }

    public void setCustomersViewMode(DataViewMode viewMode) {
        customersViewMode.set(normalizeViewMode(viewMode));
    }

    public ObjectProperty<DataViewMode> coordinatorViewModeProperty() {
        return coordinatorViewMode;
    }

    public DataViewMode getCoordinatorViewMode() {
        return normalizeViewMode(coordinatorViewMode.get());
    }

    public void setCoordinatorViewMode(DataViewMode viewMode) {
        coordinatorViewMode.set(normalizeViewMode(viewMode));
    }

    public void applySearch(SearchScope scope, String columnKey, String query) {
        searchModel.updateState(scope, columnKey, query);
        userModel.applySearch(
                searchModel.getState(SearchScope.ADMINS),
                searchModel.getState(SearchScope.EVENT_COORDINATORS)
        );
        eventModel.applySearch(searchModel.getState(SearchScope.EVENTS));
        ticketModel.applySearch(searchModel.getState(SearchScope.TICKETS));
        customerModel.applySearch(searchModel.getState(SearchScope.CUSTOMERS));
        reloadScopeAsync(scope);
    }

    private void reloadScopeAsync(SearchScope scope) {
        SearchScope targetScope = scope == null ? SearchScope.ADMINS : scope;
        Runnable loadAction = switch (targetScope) {
            case ADMINS -> () -> {
                try {
                    userModel.loadAdminAndCoordinatorUsers();
                } catch (UserException e) {
                    e.printStackTrace();
                }
            };
            case EVENTS -> () -> {
                try {
                    if (isAdmin()) {
                        eventModel.loadAllEvents();
                    } else if (getCurrentCoordinatorId() > 0) {
                        eventModel.loadEventsForCoordinator(getCurrentCoordinatorId());
                    } else {
                        Platform.runLater(eventModel.events()::clear);
                    }
                } catch (EventException e) {
                    e.printStackTrace();
                }
            };
            case EVENT_COORDINATORS -> () -> {
                try {
                    if (getCoordinatorViewMode() == DataViewMode.SELECTED_EVENT) {
                        userModel.loadCoordinatorUsersForEvent(getCurrentEventId());
                    } else {
                        userModel.loadCoordinatorUsersForAllEvents();
                    }
                } catch (UserException e) {
                    e.printStackTrace();
                }
            };
            case TICKETS -> () -> {
                try {
                    if (getTicketsViewMode() == DataViewMode.SELECTED_EVENT) {
                        ticketModel.loadTicketsForEvent(getCurrentEventId());
                    } else {
                        ticketModel.loadAllTickets();
                    }
                } catch (TicketException e) {
                    e.printStackTrace();
                }
            };
            case CUSTOMERS -> () -> {
                try {
                    if (getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                        customerModel.loadCustomersForEvent(getCurrentEventId());
                    } else {
                        customerModel.loadAllCustomers();
                    }
                } catch (CustomerException e) {
                    e.printStackTrace();
                }
            };
        };
        new Thread(loadAction, "search-scope-load-task").start();
    }

    public void loadAdminAndCoordinatorUsers() throws UserException {
        userModel.loadAdminAndCoordinatorUsers();
    }

    public void loadCoordinatorUsers() throws UserException {
        userModel.loadCoordinatorUsers();
    }

    public void loadCoordinatorUsersForEvent(long eventId) throws UserException {
        userModel.loadCoordinatorUsersForEvent(eventId);
    }

    public void loadCoordinatorUsersForAllEvents() throws UserException {
        userModel.loadCoordinatorUsersForAllEvents();
    }

    public void addCoordinatorToEvent(User user, long eventId) throws UserException {
        userModel.addCoordinatorToEvent(user, eventId);
    }

    public void removeCoordinatorFromEvent(User user, long eventId) throws UserException {
        userModel.removeCoordinatorFromEvent(user, eventId);
    }

    public User addCoordinator(User user) throws UserException {
        return userModel.addCoordinator(user);
    }

    public void updateCoordinator(User user) throws UserException {
        userModel.updateCoordinator(user);
    }

    public void setCoordinatorDeleted(User user, boolean deleted) throws UserException {
        userModel.setCoordinatorDeleted(user, deleted);
    }

    public User addAdminOrCoordinator(User user) throws UserException {
        return userModel.addAdminOrCoordinator(user);
    }

    public void updateAdminOrCoordinator(User user) throws UserException {
        userModel.updateAdminOrCoordinator(user);
    }

    public void setAdminOrCoordinatorDeleted(User user, boolean deleted) throws UserException {
        userModel.setAdminOrCoordinatorDeleted(user, deleted);
    }

    public void loadEventsForCoordinator(long coordinatorId) throws EventException {
        eventModel.loadEventsForCoordinator(coordinatorId);
    }

    public void loadAllEvents() throws EventException {
        eventModel.loadAllEvents();
    }

    public Event addEvent(Event event) throws EventException {
        return eventModel.addEvent(event);
    }

    public void updateEvent(Event event) throws EventException {
        eventModel.updateEvent(event);
    }

    public void deleteEvent(Event event, long coordinatorId) throws EventException {
        eventModel.deleteEvent(event, coordinatorId);
    }

    public void setEventDeleted(Event event, long coordinatorId, boolean deleted) throws EventException {
        eventModel.setEventDeleted(event, coordinatorId, deleted);
    }

    public void loadTicketsForEvent(long eventId) throws TicketException {
        ticketModel.loadTicketsForEvent(eventId);
    }

    public void loadAllTickets() throws TicketException {
        ticketModel.loadAllTickets();
    }

    public void loadCustomersForEvent(long eventId) throws CustomerException {
        customerModel.loadCustomersForEvent(eventId);
    }

    public void loadAllCustomers() throws CustomerException {
        customerModel.loadAllCustomers();
    }

    public Ticket addTicket(Event event,
                            Long ticketCategoryId,
                            String customerName,
                            String customerEmail,
                            String code) throws TicketException {
        return ticketModel.addTicket(event, ticketCategoryId, customerName, customerEmail, code);
    }

    public void setTicketDeleted(Ticket ticket, boolean deleted) throws TicketException {
        ticketModel.setTicketDeleted(ticket, deleted);
    }

    public void redeemTicket(Ticket ticket) throws TicketException {
        ticketModel.redeemTicket(ticket);
    }

    private DataViewMode normalizeViewMode(DataViewMode viewMode) {
        return viewMode == null ? DataViewMode.SELECTED_EVENT : viewMode;
    }
}
