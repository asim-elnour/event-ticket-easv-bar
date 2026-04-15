package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.EventTicketSystemApp;
import javafx.geometry.Rectangle2D;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

public final class SceneNavigator {

    private SceneNavigator() {
    }

    public static void openLogin(Stage stage) throws IOException {
        switchScene(stage, ViewType.LOGIN, "Event Ticket System - Login");
    }

    public static void openMain(Stage stage) throws IOException {
        switchScene(stage, ViewType.MAIN, "Event Ticket System");
        applyPrimaryScreenBounds(stage);
    }

    private static void switchScene(Stage stage, ViewType viewType, String title) throws IOException {
        if (stage == null) {
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                EventTicketSystemApp.class.getResource(viewType.getFxmlPath())
        );
        Parent root = loader.load();

        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.setMaximized(true);
        stage.show();
        root.requestFocus();
    }

    private static void applyPrimaryScreenBounds(Stage stage) {
        if (stage == null) {
            return;
        }

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        stage.setX(visualBounds.getMinX());
        stage.setY(visualBounds.getMinY());
        stage.setWidth(visualBounds.getWidth());
        stage.setHeight(visualBounds.getHeight());
        stage.setMaximized(true);
    }
}
