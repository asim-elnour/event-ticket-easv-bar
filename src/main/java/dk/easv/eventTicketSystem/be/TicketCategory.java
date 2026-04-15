package dk.easv.eventTicketSystem.be;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TicketCategory {

    private final ObjectProperty<Long> id = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> eventId = new SimpleObjectProperty<>();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> price = new SimpleObjectProperty<>();
    private final ObjectProperty<Integer> seatCount = new SimpleObjectProperty<>();
    private final BooleanProperty deleted = new SimpleBooleanProperty(false);
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();

    public TicketCategory() {
        this(null, null, null, BigDecimal.ZERO, 1, false, LocalDateTime.now());
    }

    public TicketCategory(Long id,
                          Long eventId,
                          String name,
                          BigDecimal price,
                          Integer seatCount,
                          boolean deleted,
                          LocalDateTime createdAt) {
        this.id.set(id);
        this.eventId.set(eventId);
        this.name.set(name);
        this.price.set(price);
        this.seatCount.set(seatCount);
        this.deleted.set(deleted);
        this.createdAt.set(createdAt);
    }

    public Long getId() {
        return id.get();
    }

    public void setId(Long id) {
        this.id.set(id);
    }

    public ObjectProperty<Long> idProperty() {
        return id;
    }

    public Long getEventId() {
        return eventId.get();
    }

    public void setEventId(Long eventId) {
        this.eventId.set(eventId);
    }

    public ObjectProperty<Long> eventIdProperty() {
        return eventId;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public BigDecimal getPrice() {
        return price.get();
    }

    public void setPrice(BigDecimal price) {
        this.price.set(price);
    }

    public ObjectProperty<BigDecimal> priceProperty() {
        return price;
    }

    public Integer getSeatCount() {
        return seatCount.get();
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount.set(seatCount);
    }

    public ObjectProperty<Integer> seatCountProperty() {
        return seatCount;
    }

    public boolean isDeleted() {
        return deleted.get();
    }

    public void setDeleted(boolean deleted) {
        this.deleted.set(deleted);
    }

    public BooleanProperty deletedProperty() {
        return deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt.get();
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt.set(createdAt);
    }

    public ObjectProperty<LocalDateTime> createdAtProperty() {
        return createdAt;
    }

    public TicketCategory copy() {
        return new TicketCategory(
                getId(),
                getEventId(),
                getName(),
                getPrice(),
                getSeatCount(),
                isDeleted(),
                getCreatedAt()
        );
    }

    public void restoreFrom(TicketCategory category) {
        if (category == null) {
            return;
        }
        setId(category.getId());
        setEventId(category.getEventId());
        setName(category.getName());
        setPrice(category.getPrice());
        setSeatCount(category.getSeatCount());
        setDeleted(category.isDeleted());
        setCreatedAt(category.getCreatedAt());
    }
}
