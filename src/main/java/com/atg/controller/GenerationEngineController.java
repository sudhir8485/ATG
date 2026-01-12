package com.atg.controller;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;

public class GenerationEngineController {

    @FXML
    public void initialize() {
        System.out.println("Generation Engine Loaded");
    }

    @FXML
    private void handleLoadData(ActionEvent event) {
        System.out.println("Load Subjects & Teachers clicked");
    }

    @FXML
    private void handleGenerate(ActionEvent event) {
        System.out.println("Generate Timetable clicked");
    }

    @FXML
    private void handleSave(ActionEvent event) {
        System.out.println("Save Timetable clicked");
    }
}
