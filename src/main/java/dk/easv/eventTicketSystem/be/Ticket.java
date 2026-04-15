package dk.easv.eventTicketSystem.be;

import java.time.LocalDateTime;
import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Ticket {

    private final ObjectProperty<Long> id = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> eventId = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> ticketCategoryId = new SimpleObjectProperty<>();
    private final StringProperty eventName = new SimpleStringProperty();
    private final StringProperty code = new SimpleStringProperty();
    private final StringProperty customerName = new SimpleStringProperty();
    private final StringProperty customerEmail = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> issuedAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> redeemedAt = new SimpleObjectProperty<>();
    private final BooleanProperty redeemed = new SimpleBooleanProperty(false);
    private final BooleanProperty deleted = new SimpleBooleanProperty(false);

    public Ticket() {
        this(null, null, null, null, generateCode(), null, null, LocalDateTime.now(), null, false, false);
    }

    public Ticket(Long id,
                  Long eventId,
                  Long ticketCategoryId,
                  String eventName,
                  String code,
                  String customerName,
                  String customerEmail,
                  LocalDateTime issuedAt,
                  LocalDateTime redeemedAt,
                  boolean redeemed,
                  boolean deleted) {
        this.id.set(id);
        this.eventId.set(eventId);
        this.ticketCategoryId.set(ticketCategoryId);
        this.eventName.set(eventName);
        this.code.set(code);
        this.customerName.set(customerName);
        this.customerEmail.set(customerEmail);
        this.issuedAt.set(issuedAt);
        this.redeemedAt.set(redeemedAt);
        this.redeemed.set(redeemed);
        this.deleted.set(deleted);
    }

    public static String generateCode() {
        return UUID.randomUUID().toString();
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

    public Long getTicketCategoryId() {
        return ticketCategoryId.get();
    }

    public void setTicketCategoryId(Long ticketCategoryId) {
        this.ticketCategoryId.set(ticketCategoryId);
    }

    public ObjectProperty<Long> ticketCategoryIdProperty() {
        return ticketCategoryId;
    }

    public String getEventName() {
        return eventName.get();
    }

    public void setEventName(String eventName) {
        this.eventName.set(eventName);
    }

    public StringProperty eventNameProperty() {
        return eventName;
    }

    public String getCode() {
        return code.get();
    }

    public void setCode(String code) {
        this.code.set(code);
    }

    public StringProperty codeProperty() {
        return code;
    }

    public String getCustomerName() {
        return customerName.get();
    }

    public void setCustomerName(String customerName) {
        this.customerName.set(customerName);
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail.get();
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail.set(customerEmail);
    }

    public StringProperty customerEmailProperty() {
        return customerEmail;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt.get();
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt.set(issuedAt);
    }

    public ObjectProperty<LocalDateTime> issuedAtProperty() {
        return issuedAt;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt.get();
    }

    public void setRedeemedAt(LocalDateTime redeemedAt) {
        this.redeemedAt.set(redeemedAt);
    }

    public ObjectProperty<LocalDateTime> redeemedAtProperty() {
        return redeemedAt;
    }

    public boolean isRedeemed() {
        return redeemed.get();
    }

    public void setRedeemed(boolean redeemed) {
        this.redeemed.set(redeemed);
    }

    public BooleanProperty redeemedProperty() {
        return redeemed;
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

    public String getStatusLabel() {
        if (isDeleted()) {
            return "Deleted";
        }
        if (isRedeemed()) {
            return "Redeemed";
        }
        return "Valid";
    }

    public Ticket copy() {
        return new Ticket(
                getId(),
                getEventId(),
                getTicketCategoryId(),
                getEventName(),
                getCode(),
                getCustomerName(),
                getCustomerEmail(),
                getIssuedAt(),
                getRedeemedAt(),
                isRedeemed(),
                isDeleted()
        );
    }

    public void restoreFrom(Ticket ticket) {
        if (ticket == null) {
            return;
        }
        setId(ticket.getId());
        setEventId(ticket.getEventId());
        setTicketCategoryId(ticket.getTicketCategoryId());
        setEventName(ticket.getEventName());
        setCode(ticket.getCode());
        setCustomerName(ticket.getCustomerName());
        setCustomerEmail(ticket.getCustomerEmail());
        setIssuedAt(ticket.getIssuedAt());
        setRedeemedAt(ticket.getRedeemedAt());
        setRedeemed(ticket.isRedeemed());
        setDeleted(ticket.isDeleted());
    }
}
