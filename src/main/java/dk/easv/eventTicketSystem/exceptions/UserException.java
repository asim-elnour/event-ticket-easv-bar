package dk.easv.eventTicketSystem.exceptions;

public class UserException extends Exception {
    public UserException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserException(String message) {
        super(message);
    }
}
