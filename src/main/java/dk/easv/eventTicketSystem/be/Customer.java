package dk.easv.eventTicketSystem.be;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;

public class Customer {

    private final ObjectProperty<Long> id = new SimpleObjectProperty<>();
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>(LocalDateTime.now());

    public Customer() {
    }

    public Customer(Long id, String name, String email, LocalDateTime createdAt) {
        setId(id);
        setName(name);
        setEmail(email);
        setCreatedAt(createdAt);
    }

    public Long getId() {
        return id.get();
    }

    public void setId(Long value) {
        id.set(value);
    }

    public ObjectProperty<Long> idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value == null ? "" : value.trim());
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String value) {
        email.set(value == null ? "" : value.trim());
    }

    public StringProperty emailProperty() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt.get();
    }

    public void setCreatedAt(LocalDateTime value) {
        createdAt.set(value == null ? LocalDateTime.now() : value);
    }

    public ObjectProperty<LocalDateTime> createdAtProperty() {
        return createdAt;
    }

    public Customer copy() {
        return new Customer(getId(), getName(), getEmail(), getCreatedAt());
    }

    public void restoreFrom(Customer customer) {
        if (customer == null) {
            return;
        }
        setId(customer.getId());
        setName(customer.getName());
        setEmail(customer.getEmail());
        setCreatedAt(customer.getCreatedAt());
    }

    @Override
    public String toString() {
        String safeName = getName() == null || getName().isBlank() ? "Customer" : getName();
        String safeEmail = getEmail() == null || getEmail().isBlank() ? "no-email" : getEmail();
        return safeName + " <" + safeEmail + ">";
    }
}
