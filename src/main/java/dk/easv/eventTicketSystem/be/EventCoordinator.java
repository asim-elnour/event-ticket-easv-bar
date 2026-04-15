package dk.easv.eventTicketSystem.be;

public final class EventCoordinator extends User {
    public EventCoordinator(String username, String firstName, String lastName, String email, String password) {
        super(username, firstName, lastName, email, password, Role.COORDINATOR);
    }
}
