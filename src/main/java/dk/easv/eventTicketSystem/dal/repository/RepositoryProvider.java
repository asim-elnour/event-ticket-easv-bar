package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.dal.ConnectionManager;
import dk.easv.eventTicketSystem.dal.dao.CustomerDAO;
import dk.easv.eventTicketSystem.dal.dao.Database;
import dk.easv.eventTicketSystem.dal.dao.EventDAO;
import dk.easv.eventTicketSystem.dal.dao.TicketDAO;
import dk.easv.eventTicketSystem.dal.dao.UserDAO;

public final class RepositoryProvider {

    private static final RepositoryBundle REPOSITORIES = createRepositories();

    private RepositoryProvider() {
    }

    public static UserRepository users() {
        return REPOSITORIES.users();
    }

    public static EventRepository events() {
        return REPOSITORIES.events();
    }

    public static CustomerRepository customers() {
        return REPOSITORIES.customers();
    }

    public static TicketRepository tickets() {
        return REPOSITORIES.tickets();
    }

    private static RepositoryBundle createRepositories() {
        Database database = new Database(new ConnectionManager());
        return new RepositoryBundle(
                new UserDAO(database),
                new EventDAO(database),
                new CustomerDAO(database),
                new TicketDAO(database)
        );
    }

    private record RepositoryBundle(UserRepository users,
                                    EventRepository events,
                                    CustomerRepository customers,
                                    TicketRepository tickets) {
    }
}
