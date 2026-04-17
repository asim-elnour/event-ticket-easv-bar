package dk.easv.eventTicketSystem.be;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Event {

    private final ObjectProperty<Long> id = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> coordinatorId = new SimpleObjectProperty<>();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty location = new SimpleStringProperty();
    private final StringProperty locationGuidance = new SimpleStringProperty();
    private final StringProperty notes = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> startTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> endTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> createdByUserId = new SimpleObjectProperty<>();
    private final BooleanProperty deleted = new SimpleBooleanProperty(false);
    private final ObservableList<TicketCategory> ticketTypes = FXCollections.observableArrayList();

    public Event() {
        this(null, null, null, null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                null, false, List.of());
    }

    public Event(Long id,
                 Long coordinatorId,
                 String name,
                 String location,
                 String locationGuidance,
                 String notes,
                 LocalDateTime startTime,
                 LocalDateTime endTime,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 Long createdByUserId,
                 boolean deleted,
                 List<TicketCategory> ticketTypes) {
        this.id.set(id);
        this.coordinatorId.set(coordinatorId);
        this.name.set(name);
        this.location.set(location);
        this.locationGuidance.set(locationGuidance);
        this.notes.set(notes);
        this.startTime.set(startTime);
        this.endTime.set(endTime);
        this.createdAt.set(createdAt);
        this.updatedAt.set(updatedAt);
        this.createdByUserId.set(createdByUserId);
        this.deleted.set(deleted);
        setTicketTypes(ticketTypes);
    }

    public Long getId() { return id.get(); }
    public void setId(Long id) { this.id.set(id); }
    public ObjectProperty<Long> idProperty() { return id; }

    public Long getCoordinatorId() { return coordinatorId.get(); }
    public void setCoordinatorId(Long coordinatorId) { this.coordinatorId.set(coordinatorId); }
    public ObjectProperty<Long> coordinatorIdProperty() { return coordinatorId; }

    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }
    public StringProperty nameProperty() { return name; }

    public String getLocation() { return location.get(); }
    public void setLocation(String location) { this.location.set(location); }
    public StringProperty locationProperty() { return location; }

    public String getLocationGuidance() { return locationGuidance.get(); }
    public void setLocationGuidance(String locationGuidance) { this.locationGuidance.set(locationGuidance); }
    public StringProperty locationGuidanceProperty() { return locationGuidance; }

    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }

    public LocalDateTime getStartTime() { return startTime.get(); }
    public void setStartTime(LocalDateTime startTime) { this.startTime.set(startTime); }
    public ObjectProperty<LocalDateTime> startTimeProperty() { return startTime; }

    public LocalDateTime getEndTime() { return endTime.get(); }
    public void setEndTime(LocalDateTime endTime) { this.endTime.set(endTime); }
    public ObjectProperty<LocalDateTime> endTimeProperty() { return endTime; }

    public LocalDateTime getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt.set(createdAt); }
    public ObjectProperty<LocalDateTime> createdAtProperty() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt.get(); }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt.set(updatedAt); }
    public ObjectProperty<LocalDateTime> updatedAtProperty() { return updatedAt; }

    public Long getCreatedByUserId() { return createdByUserId.get(); }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId.set(createdByUserId); }
    public ObjectProperty<Long> createdByUserIdProperty() { return createdByUserId; }

    public boolean isDeleted() { return deleted.get(); }
    public void setDeleted(boolean deleted) { this.deleted.set(deleted); }
    public BooleanProperty deletedProperty() { return deleted; }

    public ObservableList<TicketCategory> getTicketTypes() { return ticketTypes; }

    public void setTicketTypes(List<TicketCategory> categories) {
        ticketTypes.clear();
        if (categories == null) return;
        for (TicketCategory category : categories) {
            if (category != null) ticketTypes.add(category.copy());
        }
    }

    public List<TicketCategory> getTicketTypesCopy() {
        List<TicketCategory> copy = new ArrayList<>();
        for (TicketCategory category : ticketTypes) copy.add(category.copy());
        return copy;
    }

    public int getTotalSeats() {
        int total = 0;
        for (TicketCategory category : ticketTypes) {
            if (category == null || category.isDeleted()) {
                continue;
            }
            Integer seatCount = category.getSeatCount();
            total += seatCount == null ? 0 : seatCount;
        }
        return total;
    }

    public int getTotalSold() {
        int total = 0;
        for (TicketCategory category : ticketTypes) {
            if (category == null || category.isDeleted()) {
                continue;
            }
            Integer soldCount = category.getSoldCount();
            total += soldCount == null ? 0 : soldCount;
        }
        return total;
    }

    public Event copy() {
        return new Event(
                getId(),
                getCoordinatorId(),
                getName(),
                getLocation(),
                getLocationGuidance(),
                getNotes(),
                getStartTime(),
                getEndTime(),
                getCreatedAt(),
                getUpdatedAt(),
                getCreatedByUserId(),
                isDeleted(),
                getTicketTypesCopy()
        );
    }

    public void restoreFrom(Event event) {
        if (event == null) return;
        setId(event.getId());
        setCoordinatorId(event.getCoordinatorId());
        setName(event.getName());
        setLocation(event.getLocation());
        setLocationGuidance(event.getLocationGuidance());
        setNotes(event.getNotes());
        setStartTime(event.getStartTime());
        setEndTime(event.getEndTime());
        setCreatedAt(event.getCreatedAt());
        setUpdatedAt(event.getUpdatedAt());
        setCreatedByUserId(event.getCreatedByUserId());
        setDeleted(event.isDeleted());
        setTicketTypes(event.getTicketTypesCopy());
    }
}
