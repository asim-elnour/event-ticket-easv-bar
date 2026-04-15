package dk.easv.eventTicketSystem;

import dk.easv.eventTicketSystem.util.ViewType;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class EventTicketSystemApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                EventTicketSystemApp.class.getResource(ViewType.LOGIN.getFxmlPath())
        );
        Scene scene = new Scene(loader.load());
        stage.setTitle("Event Ticket System - Login");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
        scene.getRoot().requestFocus();
    }
}
