package dk.easv.eventTicketSystem.gui.dashboard;

import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.SearchScope;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardWorkspaceController implements ModelAware {

    @FXML
    private Label navTitleLabel;
    @FXML
    private ToggleButton navUsersButton;
    @FXML
    private ToggleButton navEventsButton;
    @FXML
    private ToggleButton navEventCoordinatorsButton;
    @FXML
    private ToggleButton navTicketsButton;
    @FXML
    private ToggleButton navCustomersButton;

    @FXML
    private Node usersPanel;
    @FXML
    private Node eventsPanel;
    @FXML
    private Node eventCoordinatorsPanel;
    @FXML
    private Node ticketsPanel;
    @FXML
    private Node customersPanel;

    private final ToggleGroup navigationGroup = new ToggleGroup();
    private final Map<ToggleButton, Node> navigationTargets = new LinkedHashMap<>();
    private AppModel model;

    @FXML
    public void initialize() {
        bindNavigationTexts();
        registerNavigationTarget(navUsersButton, usersPanel);
        registerNavigationTarget(navEventsButton, eventsPanel);
        registerNavigationTarget(navEventCoordinatorsButton, eventCoordinatorsPanel);
        registerNavigationTarget(navTicketsButton, ticketsPanel);
        registerNavigationTarget(navCustomersButton, customersPanel);

        navigationGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null && oldValue != null) {
                navigationGroup.selectToggle(oldValue);
                return;
            }
            showSelectedPanel(newValue);
        });
        selectInitialPanel();
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        syncActiveSearchScopeWithSelection();
    }

    private void bindNavigationTexts() {
        if (navTitleLabel != null) {
            navTitleLabel.setText("Tables");
        }
        if (navUsersButton != null) {
            navUsersButton.setText("Users");
        }
        if (navEventsButton != null) {
            navEventsButton.setText("Events");
        }
        if (navEventCoordinatorsButton != null) {
            navEventCoordinatorsButton.setText("Event Coordinators");
        }
        if (navTicketsButton != null) {
            navTicketsButton.setText("Tickets");
        }
        if (navCustomersButton != null) {
            navCustomersButton.setText("Customers");
        }
    }

    private void registerNavigationTarget(ToggleButton button, Node panel) {
        if (button == null || panel == null) {
            return;
        }
        button.setToggleGroup(navigationGroup);
        navigationTargets.put(button, panel);
    }

    private void selectInitialPanel() {
        ToggleButton initialButton = findInitialButton();
        if (initialButton == null) {
            hideAllPanels();
            return;
        }
        navigationGroup.selectToggle(initialButton);
        showSelectedPanel(initialButton);
    }

    private ToggleButton findInitialButton() {
        ToggleButton[] preferredOrder = {
                navUsersButton,
                navEventsButton,
                navEventCoordinatorsButton,
                navTicketsButton,
                navCustomersButton
        };
        for (ToggleButton candidate : preferredOrder) {
            if (candidate != null && navigationTargets.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void showSelectedPanel(Toggle selectedToggle) {
        if (!(selectedToggle instanceof ToggleButton selectedButton)) {
            hideAllPanels();
            return;
        }

        Node selectedPanel = navigationTargets.get(selectedButton);
        if (selectedPanel == null) {
            hideAllPanels();
            return;
        }

        for (Map.Entry<ToggleButton, Node> entry : navigationTargets.entrySet()) {
            boolean visible = entry.getValue() == selectedPanel;
            setPanelVisibility(entry.getValue(), visible);
            if (visible) {
                entry.getValue().toFront();
            }
        }

        updateActiveSearchScope(selectedButton);
    }

    private void hideAllPanels() {
        navigationTargets.values().forEach(panel -> setPanelVisibility(panel, false));
    }

    private void setPanelVisibility(Node panel, boolean visible) {
        panel.setVisible(visible);
        panel.setManaged(visible);
    }

    private void syncActiveSearchScopeWithSelection() {
        if (model == null) {
            return;
        }

        Toggle selected = navigationGroup.getSelectedToggle();
        if (selected instanceof ToggleButton button) {
            updateActiveSearchScope(button);
        }
    }

    private void updateActiveSearchScope(ToggleButton selectedButton) {
        if (model == null || selectedButton == null) {
            return;
        }

        SearchScope scope = scopeForButton(selectedButton);
        if (scope != null) {
            model.setActiveSearchScope(scope);
        }
    }

    private SearchScope scopeForButton(ToggleButton button) {
        if (button == navUsersButton) {
            return SearchScope.ADMINS;
        }
        if (button == navEventsButton) {
            return SearchScope.EVENTS;
        }
        if (button == navEventCoordinatorsButton) {
            return SearchScope.EVENT_COORDINATORS;
        }
        if (button == navTicketsButton) {
            return SearchScope.TICKETS;
        }
        if (button == navCustomersButton) {
            return SearchScope.CUSTOMERS;
        }
        return null;
    }
}
