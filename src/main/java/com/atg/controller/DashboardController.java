package com.atg.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.io.IOException;

public class DashboardController {

    @FXML
    private AnchorPane mainPane;

    private void loadView(String fxmlPath) throws IOException {
        AnchorPane view = FXMLLoader.load(getClass().getResource(fxmlPath));
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        mainPane.getChildren().setAll(view);
    }

    @FXML private void openInstitute() throws IOException { loadView("/InstituteConfiguration.fxml"); }
    @FXML private void openTeachers() throws IOException { loadView("/Teacher.fxml"); }
    @FXML private void openStandard() throws IOException { loadView("/Class.fxml"); }
    @FXML private void openGeneration() throws IOException { loadView("/Generation.fxml"); }
    @FXML private void openView() throws IOException { loadView("/com/atg/view/View.fxml"); }
    @FXML private void openAbout() throws IOException { loadView("/com/atg/view/About.fxml"); }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setTitle("Logout");
        alert.setContentText("You have been logged out successfully!");
        alert.showAndWait();
        // You can later replace this alert with code to return to your login screen.
    }
}
