package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.dal.ConnectionManager;
import dk.easv.eventTicketSystem.dal.repository.sql.SqlDatabase;
import dk.easv.eventTicketSystem.dal.repository.sql.SqlEventRepository;
import dk.easv.eventTicketSystem.dal.repository.sql.SqlTicketRepository;
import dk.easv.eventTicketSystem.dal.repository.sql.SqlUserRepository;

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

    public static TicketRepository tickets() {
        return REPOSITORIES.tickets();
    }

    private static RepositoryBundle createRepositories() {
        SqlDatabase database = new SqlDatabase(new ConnectionManager());
        return new RepositoryBundle(
                new SqlUserRepository(database),
                new SqlEventRepository(database),
                new SqlTicketRepository(database)
        );
    }

    private record RepositoryBundle(UserRepository users,
                                    EventRepository events,
                                    TicketRepository tickets) {
    }
}
