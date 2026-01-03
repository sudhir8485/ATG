package com.atg.controller;

import java.io.IOException;

import com.atg.util.AlertUtil;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;   // ✅ ADD THIS
import javafx.stage.Stage;
import javafx.util.Duration;


public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    private Stage stage;
    private Scene scene;

    // Hardcoded credentials
    private static final String USERNAME = "123";
    private static final String PASSWORD = "123";

    @FXML
    private void login(ActionEvent event) {
        String enteredUsername = usernameField.getText();
        String enteredPassword = passwordField.getText();

        if (enteredUsername.isEmpty() || enteredPassword.isEmpty()) {
            AlertUtil.showError("Missing Fields", "Please fill in both username and password.");
            return;
        }

        if (enteredUsername.equals(USERNAME) && enteredPassword.equals(PASSWORD)) {
            openDashboard(event);
        } else {
            AlertUtil.showError("Access Denied", "Invalid username or password.");
        }
    }

    private void openDashboard(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/Dashboard.fxml"));
            stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Automatic Timetable Generator - Dashboard");
            stage.centerOnScreen();

            // Optional fade-in transition for smooth appearance
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(800), root);
            fadeTransition.setFromValue(0.0);
            fadeTransition.setToValue(1.0);
            fadeTransition.play();

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.showError("Error", "Unable to load Dashboard.");
        }
    }
}
