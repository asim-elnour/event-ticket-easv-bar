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
    private final ObjectProperty<Long> customerId = new SimpleObjectProperty<>();
    private final StringProperty eventName = new SimpleStringProperty();
    private final StringProperty eventLocation = new SimpleStringProperty();
    private final StringProperty eventGuidance = new SimpleStringProperty();
    private final StringProperty eventNotes = new SimpleStringProperty();
    private final StringProperty code = new SimpleStringProperty();
    private final StringProperty customerName = new SimpleStringProperty();
    private final StringProperty customerEmail = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> eventStartTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> eventEndTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> issuedAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> redeemedAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> refundedAt = new SimpleObjectProperty<>();
    private final BooleanProperty redeemed = new SimpleBooleanProperty(false);

    public Ticket() {
        this(null, null, null, null, null, generateCode(), null, null, LocalDateTime.now(), null, null, false);
    }

    public Ticket(Long id,
                  Long eventId,
                  Long ticketCategoryId,
                  Long customerId,
                  String eventName,
                  String code,
                  String customerName,
                  String customerEmail,
                  LocalDateTime issuedAt,
                  LocalDateTime redeemedAt,
                  LocalDateTime refundedAt,
                  boolean redeemed) {
        this.id.set(id);
        this.eventId.set(eventId);
        this.ticketCategoryId.set(ticketCategoryId);
        this.customerId.set(customerId);
        this.eventName.set(eventName);
        this.code.set(code);
        this.customerName.set(customerName);
        this.customerEmail.set(customerEmail);
        this.issuedAt.set(issuedAt);
        this.redeemedAt.set(redeemedAt);
        this.refundedAt.set(refundedAt);
        this.redeemed.set(redeemed);
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

    public Long getCustomerId() {
        return customerId.get();
    }

    public void setCustomerId(Long customerId) {
        this.customerId.set(customerId);
    }

    public ObjectProperty<Long> customerIdProperty() {
        return customerId;
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

    public String getEventLocation() {
        return eventLocation.get();
    }

    public void setEventLocation(String eventLocation) {
        this.eventLocation.set(eventLocation);
    }

    public StringProperty eventLocationProperty() {
        return eventLocation;
    }

    public String getEventGuidance() {
        return eventGuidance.get();
    }

    public void setEventGuidance(String eventGuidance) {
        this.eventGuidance.set(eventGuidance);
    }

    public StringProperty eventGuidanceProperty() {
        return eventGuidance;
    }

    public String getEventNotes() {
        return eventNotes.get();
    }

    public void setEventNotes(String eventNotes) {
        this.eventNotes.set(eventNotes);
    }

    public StringProperty eventNotesProperty() {
        return eventNotes;
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

    public LocalDateTime getEventStartTime() {
        return eventStartTime.get();
    }

    public void setEventStartTime(LocalDateTime eventStartTime) {
        this.eventStartTime.set(eventStartTime);
    }

    public ObjectProperty<LocalDateTime> eventStartTimeProperty() {
        return eventStartTime;
    }

    public LocalDateTime getEventEndTime() {
        return eventEndTime.get();
    }

    public void setEventEndTime(LocalDateTime eventEndTime) {
        this.eventEndTime.set(eventEndTime);
    }

    public ObjectProperty<LocalDateTime> eventEndTimeProperty() {
        return eventEndTime;
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
        if (redeemedAt != null) {
            this.redeemed.set(true);
        }
    }

    public ObjectProperty<LocalDateTime> redeemedAtProperty() {
        return redeemedAt;
    }

    public boolean isRedeemed() {
        return redeemed.get() || redeemedAt.get() != null;
    }

    public void setRedeemed(boolean redeemed) {
        this.redeemed.set(redeemed);
    }

    public BooleanProperty redeemedProperty() {
        return redeemed;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt.get();
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt.set(refundedAt);
    }

    public ObjectProperty<LocalDateTime> refundedAtProperty() {
        return refundedAt;
    }

    public boolean isRefunded() {
        return refundedAt.get() != null;
    }

    public String getStatusLabel() {
        if (isRefunded()) {
            return "Refunded";
        }
        if (isRedeemed()) {
            return "Redeemed";
        }
        return "Valid";
    }

    public Ticket copy() {
        Ticket copy = new Ticket(
                getId(),
                getEventId(),
                getTicketCategoryId(),
                getCustomerId(),
                getEventName(),
                getCode(),
                getCustomerName(),
                getCustomerEmail(),
                getIssuedAt(),
                getRedeemedAt(),
                getRefundedAt(),
                isRedeemed()
        );
        copy.setEventLocation(getEventLocation());
        copy.setEventGuidance(getEventGuidance());
        copy.setEventNotes(getEventNotes());
        copy.setEventStartTime(getEventStartTime());
        copy.setEventEndTime(getEventEndTime());
        return copy;
    }

    public void restoreFrom(Ticket ticket) {
        if (ticket == null) {
            return;
        }
        setId(ticket.getId());
        setEventId(ticket.getEventId());
        setTicketCategoryId(ticket.getTicketCategoryId());
        setCustomerId(ticket.getCustomerId());
        setEventName(ticket.getEventName());
        setEventLocation(ticket.getEventLocation());
        setEventGuidance(ticket.getEventGuidance());
        setEventNotes(ticket.getEventNotes());
        setCode(ticket.getCode());
        setCustomerName(ticket.getCustomerName());
        setCustomerEmail(ticket.getCustomerEmail());
        setEventStartTime(ticket.getEventStartTime());
        setEventEndTime(ticket.getEventEndTime());
        setIssuedAt(ticket.getIssuedAt());
        setRedeemedAt(ticket.getRedeemedAt());
        setRefundedAt(ticket.getRefundedAt());
        setRedeemed(ticket.isRedeemed());
    }
}
