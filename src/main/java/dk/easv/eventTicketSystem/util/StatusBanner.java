package dk.easv.eventTicketSystem.util;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class StatusBanner {

    private final Label label;
    private PauseTransition timer;

    public StatusBanner(Label label) {
        this.label = label;
        hide();
    }

    private void clearTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void apply(String text, String cssClass) {
        clearTimer();

        label.setText(text);
        label.setVisible(true);

        label.getStyleClass().removeAll("status-saving", "status-saved", "status-failed");
        label.getStyleClass().add(cssClass);
    }

    public void showSaving(String text) {
        apply(text, "status-saving");
    }

    public void showSaving() {
        showSaving("Saving...");
    }

    public void showLoading() {
        apply("Loading...", "status-saving");
    }

    public void showPreparingTicket() {
        apply("Preparing ticket...", "status-saving");
    }

    public void showSaved() {
        hide();
    }

    public void showFailed() {
        apply("Failed", "status-failed");
        autoHide(3);
    }

    public void hide() {
        clearTimer();
        label.setVisible(false);
        label.getStyleClass().removeAll("status-saving", "status-saved", "status-failed");
    }

    private void autoHide(int seconds) {
        timer = new PauseTransition(Duration.seconds(seconds));
        timer.setOnFinished(e -> hide());
        timer.play();
    }
}
