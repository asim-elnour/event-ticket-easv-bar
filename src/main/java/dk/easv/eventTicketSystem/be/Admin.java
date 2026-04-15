package dk.easv.eventTicketSystem.be;

public final class Admin extends User {
    public Admin(String username, String firstName, String lastName, String email, String password) {
        super(username, firstName, lastName, email, password, Role.ADMIN);
    }
}
