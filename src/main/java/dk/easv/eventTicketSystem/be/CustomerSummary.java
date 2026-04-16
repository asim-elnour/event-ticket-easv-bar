package dk.easv.eventTicketSystem.be;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.LinkedHashSet;
import java.util.Set;

public class CustomerSummary {

    private final StringProperty identityKey = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final IntegerProperty ticketCount = new SimpleIntegerProperty(0);
    private final IntegerProperty validCount = new SimpleIntegerProperty(0);
    private final IntegerProperty redeemedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty deletedCount = new SimpleIntegerProperty(0);
    private final StringProperty eventsSummary = new SimpleStringProperty("");
    private final Set<String> eventNames = new LinkedHashSet<>();

    public CustomerSummary() {
    }

    public CustomerSummary(String identityKey, String name, String email) {
        setIdentityKey(identityKey);
        setName(name);
        setEmail(email);
    }

    public String getIdentityKey() {
        return identityKey.get();
    }

    public void setIdentityKey(String value) {
        identityKey.set(normalize(value));
    }

    public StringProperty identityKeyProperty() {
        return identityKey;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(normalize(value));
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String value) {
        email.set(normalize(value));
    }

    public StringProperty emailProperty() {
        return email;
    }

    public int getTicketCount() {
        return ticketCount.get();
    }

    public IntegerProperty ticketCountProperty() {
        return ticketCount;
    }

    public int getValidCount() {
        return validCount.get();
    }

    public IntegerProperty validCountProperty() {
        return validCount;
    }

    public int getRedeemedCount() {
        return redeemedCount.get();
    }

    public IntegerProperty redeemedCountProperty() {
        return redeemedCount;
    }

    public int getDeletedCount() {
        return deletedCount.get();
    }

    public IntegerProperty deletedCountProperty() {
        return deletedCount;
    }

    public String getEventsSummary() {
        return eventsSummary.get();
    }

    public StringProperty eventsSummaryProperty() {
        return eventsSummary;
    }

    public void includeTicket(Ticket ticket) {
        if (ticket == null) {
            return;
        }

        String candidateName = normalize(ticket.getCustomerName());
        String candidateEmail = normalize(ticket.getCustomerEmail());
        if (getName().isBlank() && !candidateName.isBlank()) {
            setName(candidateName);
        }
        if (getEmail().isBlank() && !candidateEmail.isBlank()) {
            setEmail(candidateEmail);
        }

        ticketCount.set(ticketCount.get() + 1);
        if (ticket.isDeleted()) {
            deletedCount.set(deletedCount.get() + 1);
        } else if (ticket.isRedeemed()) {
            redeemedCount.set(redeemedCount.get() + 1);
        } else {
            validCount.set(validCount.get() + 1);
        }

        String eventName = normalize(ticket.getEventName());
        if (!eventName.isBlank()) {
            eventNames.add(eventName);
            eventsSummary.set(summarizeEvents());
        }
    }

    public CustomerSummary copy() {
        CustomerSummary copy = new CustomerSummary(getIdentityKey(), getName(), getEmail());
        copy.ticketCount.set(getTicketCount());
        copy.validCount.set(getValidCount());
        copy.redeemedCount.set(getRedeemedCount());
        copy.deletedCount.set(getDeletedCount());
        copy.eventNames.addAll(eventNames);
        copy.eventsSummary.set(getEventsSummary());
        return copy;
    }

    private String summarizeEvents() {
        if (eventNames.isEmpty()) {
            return "";
        }
        if (eventNames.size() <= 3) {
            return String.join(", ", eventNames);
        }

        String[] names = eventNames.toArray(String[]::new);
        return String.join(", ", names[0], names[1], names[2]) + " and " + (names.length - 3) + " more";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
