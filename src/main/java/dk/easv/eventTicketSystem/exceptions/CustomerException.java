package dk.easv.eventTicketSystem.exceptions;

public class CustomerException extends Exception {
    public CustomerException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomerException(String message) {
        super(message);
    }
}
