package dk.easv.eventTicketSystem.gui.search;

import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.SearchModel;
import dk.easv.eventTicketSystem.gui.model.SearchScope;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.SceneNavigator;
import dk.easv.eventTicketSystem.util.SessionManager;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public class SearchBarController implements ModelAware {

    @FXML
    private ChoiceBox<SearchModel.SearchColumnOption> searchScopeChoice;
    @FXML
    private TextField filterField;
    @FXML
    private Button clearFilterButton;
    @FXML
    private Button logoutButton;
    @FXML
    private Label currentUserLabel;
    @FXML
    private ScrollPane topBarScroll;

    private AppModel model;
    private PauseTransition searchDebounce;
    private boolean restoringSearchState;

    @FXML
    public void initialize() {
        setupSearchControls();
        configureHorizontalOnlyScroll();
        refreshCurrentUserLabel();
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        if (this.model == null) {
            return;
        }
        refreshCurrentUserLabel();
        this.model.activeSearchScopeProperty().addListener((obs, oldValue, newValue) -> restoreSearchStateForScope(newValue));
        restoreSearchStateForScope(this.model.getActiveSearchScope());
    }

    private void setupSearchControls() {
        refreshSearchScopeChoice(SearchScope.ADMINS, SearchModel.COLUMN_ALL);

        filterField.setPromptText("Search...");
        clearFilterButton.setText("X");
        logoutButton.setText("Log Out");
        logoutButton.setOnAction(event -> onLogout());

        clearFilterButton.setVisible(false);
        clearFilterButton.managedProperty().bind(clearFilterButton.visibleProperty());
        clearFilterButton.setOnAction(event -> filterField.clear());

        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(event -> applySearch());

        filterField.textProperty().addListener((obs, oldValue, newValue) -> {
            clearFilterButton.setVisible(newValue != null && !newValue.isBlank());
            if (restoringSearchState) {
                return;
            }
            searchDebounce.playFromStart();
        });

        searchScopeChoice.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (restoringSearchState) {
                return;
            }
            applySearch();
        });
    }

    private void applySearch() {
        if (model == null || restoringSearchState) {
            return;
        }

        SearchScope scope = currentScope();
        SearchModel.SearchColumnOption selectedColumn = searchScopeChoice.getValue();
        String columnKey = selectedColumn == null ? SearchModel.COLUMN_ALL : selectedColumn.key();
        model.applySearch(scope, columnKey, filterField.getText());
    }

    private SearchScope currentScope() {
        if (model == null) {
            return SearchScope.ADMINS;
        }
        SearchScope activeScope = model.getActiveSearchScope();
        return activeScope == null ? SearchScope.ADMINS : activeScope;
    }

    private void restoreSearchStateForScope(SearchScope scope) {
        SearchScope activeScope = scope == null ? SearchScope.ADMINS : scope;
        SearchModel.SearchState state = model == null
                ? new SearchModel.SearchState(SearchModel.COLUMN_ALL, "")
                : model.getSearchState(activeScope);

        restoringSearchState = true;
        refreshSearchScopeChoice(activeScope, state.columnKey());
        filterField.setText(state.query());
        clearFilterButton.setVisible(state.query() != null && !state.query().isBlank());
        restoringSearchState = false;

        if (model != null) {
            model.reapplySearchFilters();
        }
    }

    private void refreshSearchScopeChoice(SearchScope scope, String selectedColumnKey) {
        List<SearchModel.SearchColumnOption> options = model == null
                ? List.of(new SearchModel.SearchColumnOption(SearchModel.COLUMN_ALL, "All"))
                : model.search().columnsFor(scope);

        searchScopeChoice.getItems().setAll(options);
        if (options.isEmpty()) {
            searchScopeChoice.setValue(null);
            return;
        }

        SearchModel.SearchColumnOption selected = options.stream()
                .filter(option -> option.key().equals(selectedColumnKey))
                .findFirst()
                .orElse(options.get(0));
        searchScopeChoice.setValue(selected);
    }

    private void onLogout() {
        SessionManager.clearCurrentUser();
        if (logoutButton == null || logoutButton.getScene() == null
                || !(logoutButton.getScene().getWindow() instanceof Stage stage)) {
            return;
        }

        try {
            SceneNavigator.openLogin(stage);
        } catch (Exception e) {
            DialogUtils.showError(
                    "Log Out Failed",
                    null,
                    "Could not return to login page: " + e.getMessage()
            );
        }
    }

    private void refreshCurrentUserLabel() {
        if (currentUserLabel == null) {
            return;
        }

        User currentUser = SessionManager.getCurrentUser();
        String displayName = "";
        if (currentUser != null) {
            String fullName = currentUser.getFullName();
            displayName = fullName == null || fullName.isBlank()
                    ? currentUser.getUsername()
                    : fullName;
        }

        boolean hasName = displayName != null && !displayName.isBlank();
        currentUserLabel.setText(hasName ? displayName.trim() : "");
        currentUserLabel.setVisible(hasName);
        currentUserLabel.setManaged(hasName);
    }

    private void configureHorizontalOnlyScroll() {
        if (topBarScroll == null) {
            return;
        }

        if (topBarScroll.getContent() instanceof Region contentRegion) {
            contentRegion.minWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> {
                        double viewportWidth = topBarScroll.getViewportBounds().getWidth();
                        return viewportWidth <= 0 ? Region.USE_COMPUTED_SIZE : viewportWidth;
                    },
                    topBarScroll.viewportBoundsProperty()
            ));
        }

        topBarScroll.vvalueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue.doubleValue() != 0.0) {
                topBarScroll.setVvalue(0.0);
            }
        });

        topBarScroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (Math.abs(event.getDeltaY()) <= Math.abs(event.getDeltaX())) {
                return;
            }

            if (topBarScroll.getContent() == null) {
                return;
            }

            double contentWidth = topBarScroll.getContent().getBoundsInLocal().getWidth();
            double viewportWidth = topBarScroll.getViewportBounds().getWidth();
            double scrollableWidth = contentWidth - viewportWidth;

            if (scrollableWidth <= 0) {
                return;
            }

            double delta = -event.getDeltaY() / scrollableWidth;
            double next = Math.max(0.0, Math.min(1.0, topBarScroll.getHvalue() + delta));
            topBarScroll.setHvalue(next);
            topBarScroll.setVvalue(0.0);
            event.consume();
        });
    }
}
