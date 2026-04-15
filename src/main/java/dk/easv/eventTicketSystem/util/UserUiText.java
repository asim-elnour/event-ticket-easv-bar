package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;

public final class UserUiText {

    private UserUiText() {
    }

    public static String roleLabel(Role role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case ADMIN -> "Admin";
            case COORDINATOR -> "Event Coordinator";
        };
    }

    public static String statusLabel(User user) {
        if (user == null) {
            return "";
        }
        if (user.isDeleted()) {
            return "Deleted";
        }
        return "Active";
    }
}
