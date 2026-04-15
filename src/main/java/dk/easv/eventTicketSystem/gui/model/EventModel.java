package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.bll.EventLogic;
import dk.easv.eventTicketSystem.exceptions.EventException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class EventModel {

    private final EventLogic eventLogic = new EventLogic();
    private final ObservableList<Event> events = FXCollections.observableArrayList();
    private final SortedList<Event> eventsSorted = new SortedList<>(events);
    private final AtomicLong eventsRequestVersion = new AtomicLong(0);

    private boolean showDeletedEvents = true;
    private SearchModel.SearchState eventsSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");

    public ObservableList<Event> events() {
        return events;
    }

    public SortedList<Event> eventsView() {
        return eventsSorted;
    }

    public void applySearch(SearchModel.SearchState state) {
        eventsSearchState = state == null
                ? new SearchModel.SearchState(SearchModel.COLUMN_ALL, "")
                : state;
    }

    public boolean isShowDeletedEvents() {
        return showDeletedEvents;
    }

    public void setShowDeletedEvents(boolean showDeletedEvents) {
        this.showDeletedEvents = showDeletedEvents;
    }

    public void loadEventsForCoordinator(long coordinatorId) throws EventException {
        long requestVersion = eventsRequestVersion.incrementAndGet();
        List<Event> loaded = eventLogic.searchEventsForCoordinator(
                coordinatorId,
                eventsSearchState.columnKey(),
                eventsSearchState.query(),
                showDeletedEvents
        );
        Platform.runLater(() -> {
            if (requestVersion == eventsRequestVersion.get()) {
                events.setAll(loaded);
            }
        });
    }

    public void loadAllEvents() throws EventException {
        long requestVersion = eventsRequestVersion.incrementAndGet();
        List<Event> loaded = eventLogic.searchAllEvents(
                eventsSearchState.columnKey(),
                eventsSearchState.query(),
                showDeletedEvents
        );
        Platform.runLater(() -> {
            if (requestVersion == eventsRequestVersion.get()) {
                events.setAll(loaded);
            }
        });
    }

    public Event addEvent(Event event) throws EventException {
        return eventLogic.addEvent(event);
    }

    public void updateEvent(Event event) throws EventException {
        eventLogic.updateEvent(event);
    }

    public void deleteEvent(Event event, long coordinatorId) throws EventException {
        setEventDeleted(event, coordinatorId, true);
    }

    public void setEventDeleted(Event event, long coordinatorId, boolean deleted) throws EventException {
        eventLogic.setEventDeleted(event.getId(), coordinatorId, deleted);
    }
}
