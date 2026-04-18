package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.be.User;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

final class DaoSupport {

    private DaoSupport() {
    }

    static User mapUser(ResultSet rs) throws SQLException {
        Role role = Role.fromId(rs.getInt("role_id"));
        User user = new User(
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("password"),
                role
        );
        user.idProperty().set(getLongObject(rs, "id"));
        user.setPhone(rs.getString("phone"));
        user.setDeleted(rs.getBoolean("is_deleted"));
        user.createdAtProperty().set(getLocalDateTime(rs, "created_at"));
        user.setEventCoordinatorEventId(getLongObject(rs, "coordinator_event_id"));
        user.setEventCoordinatorEventName(rs.getString("coordinator_event_name"));
        user.setEventCoordinatorRemoved(rs.getBoolean("coordinator_removed"));
        return user;
    }

    static Event mapEvent(ResultSet rs, List<TicketCategory> ticketCategories) throws SQLException {
        return new Event(
                getLongObject(rs, "id"),
                getLongObject(rs, "coordinator_id"),
                rs.getString("name"),
                rs.getString("location"),
                rs.getString("location_guidance"),
                rs.getString("notes"),
                getLocalDateTime(rs, "start_time"),
                getLocalDateTime(rs, "end_time"),
                getLocalDateTime(rs, "created_at"),
                getLocalDateTime(rs, "updated_at"),
                getLongObject(rs, "created_by_user_id"),
                rs.getBoolean("is_deleted"),
                ticketCategories
        );
    }

    static TicketCategory mapTicketCategory(ResultSet rs) throws SQLException {
        TicketCategory category = new TicketCategory(
                getLongObject(rs, "id"),
                getLongObject(rs, "event_id"),
                rs.getString("name"),
                rs.getBigDecimal("price") == null ? BigDecimal.ZERO : rs.getBigDecimal("price"),
                rs.getObject("seat_count", Integer.class),
                rs.getObject("sold_count", Integer.class),
                rs.getBoolean("is_deleted"),
                getLocalDateTime(rs, "created_at")
        );
        category.setRefundedCount(rs.getObject("refunded_count", Integer.class));
        category.setRedeemedCount(rs.getObject("redeemed_count", Integer.class));
        return category;
    }

    static Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                getLongObject(rs, "id"),
                rs.getString("name"),
                rs.getString("email"),
                getLocalDateTime(rs, "created_at")
        );
    }

    static Ticket mapTicket(ResultSet rs) throws SQLException {
        Ticket ticket = new Ticket(
                getLongObject(rs, "id"),
                getLongObject(rs, "event_id"),
                getLongObject(rs, "ticket_category_id"),
                getLongObject(rs, "customer_id"),
                rs.getString("event_name"),
                rs.getString("code"),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                getLocalDateTime(rs, "issued_at"),
                getLocalDateTime(rs, "redeemed_at"),
                getLocalDateTime(rs, "refunded_at"),
                rs.getBoolean("redeemed")
        );
        ticket.setTicketCategoryName(rs.getString("ticket_category_name"));
        ticket.setEventLocation(rs.getString("event_location"));
        ticket.setEventGuidance(rs.getString("event_guidance"));
        ticket.setEventNotes(rs.getString("event_notes"));
        ticket.setEventStartTime(getLocalDateTime(rs, "event_start_time"));
        ticket.setEventEndTime(getLocalDateTime(rs, "event_end_time"));
        return ticket;
    }

    static Long getLongObject(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    static void setLongOrNull(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value == null || value <= 0) {
            stmt.setNull(index, Types.BIGINT);
            return;
        }
        stmt.setLong(index, value);
    }

    static void setTimestampOrNull(PreparedStatement stmt, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.TIMESTAMP);
            return;
        }
        stmt.setTimestamp(index, Timestamp.valueOf(value));
    }

    static String likePattern(String query) {
        return "%" + safe(query).toLowerCase() + "%";
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
