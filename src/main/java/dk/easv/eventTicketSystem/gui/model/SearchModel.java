package dk.easv.eventTicketSystem.gui.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SearchModel {

    public static final String COLUMN_ALL = "all";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_ROLE = "role";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_EVENT = "event";
    public static final String COLUMN_LOCATION = "location";
    public static final String COLUMN_START = "start";
    public static final String COLUMN_CODE = "code";
    public static final String COLUMN_CUSTOMER = "customer";

    private static final SearchColumnOption ALL_COLUMN =
            new SearchColumnOption(COLUMN_ALL, "All");

    private static final Map<SearchScope, List<SearchColumnOption>> AVAILABLE_COLUMNS = Map.of(
            SearchScope.ADMINS, List.of(
                    ALL_COLUMN,
                    new SearchColumnOption(COLUMN_USERNAME, "Username"),
                    new SearchColumnOption(COLUMN_NAME, "Name"),
                    new SearchColumnOption(COLUMN_ROLE, "Role"),
                    new SearchColumnOption(COLUMN_STATUS, "Status")
            ),
            SearchScope.EVENTS, List.of(
                    ALL_COLUMN,
                    new SearchColumnOption(COLUMN_NAME, "Name"),
                    new SearchColumnOption(COLUMN_LOCATION, "Location"),
                    new SearchColumnOption(COLUMN_START, "Start"),
                    new SearchColumnOption(COLUMN_STATUS, "Status")
            ),
            SearchScope.EVENT_COORDINATORS, List.of(
                    ALL_COLUMN,
                    new SearchColumnOption(COLUMN_USERNAME, "Username"),
                    new SearchColumnOption(COLUMN_NAME, "Name"),
                    new SearchColumnOption(COLUMN_EVENT, "Event"),
                    new SearchColumnOption(COLUMN_STATUS, "Status")
            ),
            SearchScope.TICKETS, List.of(
                    ALL_COLUMN,
                    new SearchColumnOption(COLUMN_CODE, "Code"),
                    new SearchColumnOption(COLUMN_CUSTOMER, "Customer"),
                    new SearchColumnOption(COLUMN_EVENT, "Event"),
                    new SearchColumnOption(COLUMN_STATUS, "Status")
            ),
            SearchScope.CUSTOMERS, List.of(
                    ALL_COLUMN,
                    new SearchColumnOption(COLUMN_CUSTOMER, "Customer"),
                    new SearchColumnOption(COLUMN_EVENT, "Event"),
                    new SearchColumnOption(COLUMN_STATUS, "Status")
            )
    );

    private final ObjectProperty<SearchScope> activeScope = new SimpleObjectProperty<>(SearchScope.ADMINS);
    private final EnumMap<SearchScope, SearchState> states = new EnumMap<>(SearchScope.class);

    public SearchModel() {
        for (SearchScope scope : SearchScope.values()) {
            states.put(scope, new SearchState(COLUMN_ALL, ""));
        }
    }

    public ObjectProperty<SearchScope> activeScopeProperty() {
        return activeScope;
    }

    public SearchScope getActiveScope() {
        return activeScope.get();
    }

    public void setActiveScope(SearchScope scope) {
        activeScope.set(normalizeScope(scope));
    }

    public List<SearchColumnOption> columnsFor(SearchScope scope) {
        return AVAILABLE_COLUMNS.getOrDefault(normalizeScope(scope), List.of(ALL_COLUMN));
    }

    public SearchState getState(SearchScope scope) {
        SearchScope normalizedScope = normalizeScope(scope);
        return states.getOrDefault(normalizedScope, new SearchState(COLUMN_ALL, ""));
    }

    public void updateState(SearchScope scope, String columnKey, String query) {
        SearchScope normalizedScope = normalizeScope(scope);
        String normalizedColumn = normalizeColumnKey(normalizedScope, columnKey);
        String normalizedQuery = query == null ? "" : query;
        states.put(normalizedScope, new SearchState(normalizedColumn, normalizedQuery));
    }

    public String normalizeColumnKey(SearchScope scope, String candidateKey) {
        String normalizedKey = candidateKey == null ? "" : candidateKey.trim();
        if (normalizedKey.isEmpty()) {
            return COLUMN_ALL;
        }
        for (SearchColumnOption option : columnsFor(scope)) {
            if (option.key().equals(normalizedKey)) {
                return normalizedKey;
            }
        }
        return COLUMN_ALL;
    }

    private SearchScope normalizeScope(SearchScope scope) {
        return scope == null ? SearchScope.ADMINS : scope;
    }

    public record SearchState(String columnKey, String query) {
        public SearchState {
            columnKey = columnKey == null || columnKey.isBlank() ? COLUMN_ALL : columnKey;
            query = query == null ? "" : query;
        }
    }

    public record SearchColumnOption(String key, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
