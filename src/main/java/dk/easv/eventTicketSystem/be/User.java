package dk.easv.eventTicketSystem.be;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;

public class User {

    private final ObjectProperty<Long> id = new SimpleObjectProperty<>();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty lastName = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final ObjectProperty<Role> role = new SimpleObjectProperty<>();
    private final BooleanProperty deleted = new SimpleBooleanProperty(false);
    private final BooleanProperty eventCoordinatorRemoved = new SimpleBooleanProperty(false);
    private final ObjectProperty<Long> eventCoordinatorEventId = new SimpleObjectProperty<>();
    private final StringProperty eventCoordinatorEventName = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>(LocalDateTime.now());

    public User(String username, String firstName, String lastName, String email, String password, Role role) {
        setUsername(username);
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        setPassword(password);
        setRole(role);
    }

    public Long getId() { return id.get(); }
    public ObjectProperty<Long> idProperty() { return id; }

    public String getUsername() { return username.get(); }
    public void setUsername(String u) { username.set(u); }
    public StringProperty usernameProperty() { return username; }

    public String getFirstName() { return firstName.get(); }
    public void setFirstName(String firstName) { this.firstName.set(firstName); }
    public StringProperty firstNameProperty() { return firstName; }

    public String getLastName() { return lastName.get(); }
    public void setLastName(String lastName) { this.lastName.set(lastName); }
    public StringProperty lastNameProperty() { return lastName; }

    public String getFullName() {
        String first = getFirstName() == null ? "" : getFirstName().trim();
        String last = getLastName() == null ? "" : getLastName().trim();
        return (first + " " + last).trim();
    }

    public String getEmail() { return email.get(); }
    public void setEmail(String e) { email.set(e); }
    public StringProperty emailProperty() { return email; }

    public String getPhone() { return phone.get(); }
    public void setPhone(String p) { phone.set(p); }
    public StringProperty phoneProperty() { return phone; }

    public String getPassword() { return password.get(); }
    public void setPassword(String h) { password.set(h); }
    public StringProperty passwordProperty() { return password; }

    public Role getRole() { return role.get(); }
    public void setRole(Role r) { role.set(r); }
    public ObjectProperty<Role> roleProperty() { return role; }

    public boolean hasRole(Role role) {
        return role != null && role == getRole();
    }

    public boolean isDeleted() { return deleted.get(); }
    public void setDeleted(boolean deleted) { this.deleted.set(deleted); }
    public BooleanProperty deletedProperty() { return deleted; }

    public boolean isEventCoordinatorRemoved() { return eventCoordinatorRemoved.get(); }
    public void setEventCoordinatorRemoved(boolean removed) { eventCoordinatorRemoved.set(removed); }
    public BooleanProperty eventCoordinatorRemovedProperty() { return eventCoordinatorRemoved; }

    public Long getEventCoordinatorEventId() { return eventCoordinatorEventId.get(); }
    public void setEventCoordinatorEventId(Long eventId) { eventCoordinatorEventId.set(eventId); }
    public ObjectProperty<Long> eventCoordinatorEventIdProperty() { return eventCoordinatorEventId; }

    public String getEventCoordinatorEventName() { return eventCoordinatorEventName.get(); }
    public void setEventCoordinatorEventName(String eventName) { eventCoordinatorEventName.set(eventName); }
    public StringProperty eventCoordinatorEventNameProperty() { return eventCoordinatorEventName; }

    public LocalDateTime getCreatedAt() { return createdAt.get(); }
    public ObjectProperty<LocalDateTime> createdAtProperty() { return createdAt; }

    public String getStatusLabel() {
        if (isDeleted()) {
            return "Deleted";
        }
        return "Active";
    }

    public User copy() {
        User copy = new User(
                getUsername(),
                getFirstName(),
                getLastName(),
                getEmail(),
                getPassword(),
                getRole()
        );
        copy.idProperty().set(getId());
        copy.setPhone(getPhone());
        copy.setDeleted(isDeleted());
        copy.setEventCoordinatorRemoved(isEventCoordinatorRemoved());
        copy.setEventCoordinatorEventId(getEventCoordinatorEventId());
        copy.setEventCoordinatorEventName(getEventCoordinatorEventName());
        copy.createdAtProperty().set(getCreatedAt());
        return copy;
    }

    public void restoreFrom(User user) {
        if (user == null) {
            return;
        }
        idProperty().set(user.getId());
        setUsername(user.getUsername());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setEmail(user.getEmail());
        setPhone(user.getPhone());
        setPassword(user.getPassword());
        setRole(user.getRole());
        setDeleted(user.isDeleted());
        setEventCoordinatorRemoved(user.isEventCoordinatorRemoved());
        setEventCoordinatorEventId(user.getEventCoordinatorEventId());
        setEventCoordinatorEventName(user.getEventCoordinatorEventName());
        createdAtProperty().set(user.getCreatedAt());
    }

    @Override
    public String toString() {
        return getFullName() + " (" + getUsername() + ")";
    }
}
