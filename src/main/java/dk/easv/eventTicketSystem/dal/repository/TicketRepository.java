package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.exceptions.TicketException;

import java.util.List;

public interface TicketRepository {

    List<Ticket> getTicketsForEvent(long eventId) throws TicketException;

    List<Ticket> getAllTickets() throws TicketException;

    List<Ticket> searchTicketsForEvent(long eventId, String columnKey, String query, boolean includeRefunded) throws TicketException;

    List<Ticket> searchAllTickets(String columnKey, String query, boolean includeRefunded) throws TicketException;

    Ticket addTicket(long eventId,
                     Long ticketCategoryId,
                     long customerId,
                     String code) throws TicketException;

    Ticket getTicketById(long ticketId) throws TicketException;

    int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) throws TicketException;

    Ticket refundTicketById(long ticketId) throws TicketException;

    Ticket redeemTicketById(long ticketId) throws TicketException;
}
