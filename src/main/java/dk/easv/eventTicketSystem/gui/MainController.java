package dk.easv.eventTicketSystem.gui;

import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.search.SearchBarController;
import dk.easv.eventTicketSystem.util.SceneNavigator;
import dk.easv.eventTicketSystem.util.SessionManager;
import dk.easv.eventTicketSystem.util.ViewType;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainController {

    @FXML
    private BorderPane root;

    @FXML
    private StackPane contentRoot;

    @FXML
    private StackPane topBarRoot;

    @FXML
    private ScrollPane topBarScroll;

    @FXML
    private Label footerLabel;

    private AppModel model;
    private SearchBarController searchBarController;

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        if (footerLabel != null) {
            footerLabel.setText("\u00A9 2026 Asim El Nour");
        }

        model = new AppModel();

        loadTopBar();

        loadDashboard(ViewType.DASHBOARD);

        if (searchBarController != null) {
            searchBarController.setModel(model);
        }

        updateWindowTitle();
    }

    private void redirectToLogin() {
        Platform.runLater(() -> {
            if (root == null || root.getScene() == null || !(root.getScene().getWindow() instanceof Stage stage)) {
                return;
            }

            try {
                SceneNavigator.openLogin(stage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateWindowTitle() {
        if (root == null || root.getScene() == null || !(root.getScene().getWindow() instanceof Stage stage)) {
            return;
        }
        stage.setTitle("Event Ticket System");
    }

    private void loadTopBar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(ViewType.SEARCH_BAR.getFxmlPath())
            );
            Parent topBar = loader.load();
            searchBarController = loader.getController();
            topBarRoot.getChildren().setAll(topBar);
            configureTopBarSizing(topBar);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configureTopBarSizing(Parent topBar) {
        if (topBarScroll == null || !(topBar instanceof Region region)) {
            return;
        }
        region.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> topBarScroll.getViewportBounds() == null
                        ? Region.USE_COMPUTED_SIZE
                        : topBarScroll.getViewportBounds().getWidth(),
                topBarScroll.viewportBoundsProperty()
        ));
    }

    private void loadDashboard(ViewType viewType) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(viewType.getFxmlPath())
            );

            loader.setControllerFactory(clazz -> {
                try {
                    Object controller = clazz.getDeclaredConstructor().newInstance();

                    if (controller instanceof ModelAware) {
                        ((ModelAware) controller).setModel(model);
                    }

                    return controller;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Parent view = loader.load();
            contentRoot.getChildren().setAll(view);
            updateWindowTitle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
