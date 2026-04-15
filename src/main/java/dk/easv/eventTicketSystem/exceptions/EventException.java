package dk.easv.eventTicketSystem.exceptions;

public class EventException extends Exception {

    public enum ErrorType {
        DATABASE_ERROR,
        NOT_FOUND,
        VALIDATION_ERROR,
        UNKNOWN
    }

    private final ErrorType type;

    public EventException(String message, Throwable cause, ErrorType type) {
        super(message, cause);
        this.type = type;
    }

    public EventException(String message, ErrorType type) {
        super(message);
        this.type = type;
    }

    public EventException(String message, Throwable cause) {
        super(message, cause);
        this.type = ErrorType.UNKNOWN;
    }

    public ErrorType getType() {
        return type;
    }
}
