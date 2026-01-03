package com.atg.controller;

import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.atg.database.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class InstituteConfigurationController {

    // Editable fields
    @FXML private TextField instituteNameField;
    @FXML private TextField departmentNameField;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private TextField workingDaysField;
    @FXML private TextField practicalDurationField;
    @FXML private TextField lectureDurationField;

    @FXML private TableView<BreakEntry> breakTable;
    @FXML private TableColumn<BreakEntry, Integer> colNo;
    @FXML private TableColumn<BreakEntry, String> colStart;
    @FXML private TableColumn<BreakEntry, String> colEnd;

    // View States
    @FXML private VBox editForm;     // form with textfields
    @FXML private VBox savedView;    // read-only saved data

    // Saved Data Labels
    @FXML private Label lblSavedName;
    @FXML private Label lblSavedDept;
    @FXML private Label lblSavedTime;
    @FXML private Label lblSavedDays;
    @FXML private Label lblSavedDurations;
    @FXML private Label lblSavedWorkingSlots;

    private final ObservableList<BreakEntry> breakData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colNo.setCellValueFactory(cell -> cell.getValue().noProperty().asObject());
        colStart.setCellValueFactory(cell -> cell.getValue().startProperty());
        colEnd.setCellValueFactory(cell -> cell.getValue().endProperty());
        breakTable.setItems(breakData);

        loadInstituteData();
    }

    // -----------------------------------
    // ADD BREAK
    // -----------------------------------
    @FXML
    private void handleAddBreak() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Break");
        dialog.setHeaderText("Enter Break Start and End Time (e.g., 10:30 AM - 10:45 AM)");
        dialog.setContentText("Break (start - end):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                String[] times = input.split("-");
                if (times.length == 2) {
                    String start = times[0].trim();
                    String end = times[1].trim();
                    breakData.add(new BreakEntry(breakData.size() + 1, start, end));
                }
            } catch (Exception e) {
                showAlert("Error", "Invalid input format!");
            }
        });
    }

    // -----------------------------------
    // DELETE BREAK
    // -----------------------------------
    @FXML
    private void handleDeleteBreak() {
        BreakEntry selected = breakTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            breakData.remove(selected);
            for (int i = 0; i < breakData.size(); i++) {
                breakData.get(i).setNo(i + 1);
            }
        } else {
            showAlert("Warning", "Please select a break to delete!");
        }
    }

    // -----------------------------------
    // CONFIRM SAVE (INSERT CONFIG)
    // -----------------------------------
    @FXML
    private void handleConfirm() {

        String name = instituteNameField.getText();
        String dept = departmentNameField.getText();
        String start = startTimeField.getText();
        String end = endTimeField.getText();
        String days = workingDaysField.getText();
        String pracDur = practicalDurationField.getText();
        String lecDur = lectureDurationField.getText();

        if (name.isEmpty() || start.isEmpty() || end.isEmpty() || days.isEmpty()
                || lecDur.isEmpty() || pracDur.isEmpty()) {
            showAlert("Error", "Please fill all required fields!");
            return;
        }

        int lectureDuration = Integer.parseInt(lecDur);
        int workingSlots = calculateWorkingSlots(start, end, breakData, lectureDuration);

        try (Connection con = DBConnection.getConnection()) {
            String sql = "INSERT INTO institute_config " +
                    "(name, department, start_time, end_time, working_days, " +
                    "practical_duration, lecture_duration, working_slots) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, name);
            ps.setString(2, dept);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setInt(5, Integer.parseInt(days));
            ps.setInt(6, Integer.parseInt(pracDur));
            ps.setInt(7, Integer.parseInt(lecDur));
            ps.setInt(8, workingSlots);

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            int instituteId = 0;
            if (rs.next()) instituteId = rs.getInt(1);

            // Insert breaks
            for (BreakEntry b : breakData) {
                PreparedStatement ps2 = con.prepareStatement(
                        "INSERT INTO institute_breaks (institute_id, break_no, start_time, end_time) VALUES (?, ?, ?, ?)");
                ps2.setInt(1, instituteId);
                ps2.setInt(2, b.getNo());
                ps2.setString(3, b.getStart());
                ps2.setString(4, b.getEnd());
                ps2.executeUpdate();
            }

            showAlert("Success", "Configuration saved!\nTotal Slots: " + workingSlots);

            showSavedState(name, dept, start, end, days, pracDur, lecDur, workingSlots);
            clearFields();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Error saving data: " + e.getMessage());
        }
    }

    // -----------------------------------
    // CALCULATE WORKING SLOTS
    // -----------------------------------
    private int calculateWorkingSlots(String start, String end, List<BreakEntry> breaks, int lectureDuration) {

        start = normalizeTimeFormat(start);
        end = normalizeTimeFormat(end);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        LocalTime s = LocalTime.parse(start, fmt);
        LocalTime e = LocalTime.parse(end, fmt);

        double totalMinutes = java.time.Duration.between(s, e).toMinutes();

        for (BreakEntry b : breaks) {
            try {
                String bStart = normalizeTimeFormat(b.getStart());
                String bEnd = normalizeTimeFormat(b.getEnd());
                LocalTime bs = LocalTime.parse(bStart, fmt);
                LocalTime be = LocalTime.parse(bEnd, fmt);

                totalMinutes -= java.time.Duration.between(bs, be).toMinutes();

            } catch (Exception ex) {
                System.err.println("Skipping invalid break: " + ex.getMessage());
            }
        }

        return (int) Math.floor(totalMinutes / lectureDuration);
    }

    // -----------------------------------
    // SHOW SAVED DATA VIEW (READ ONLY)
    // -----------------------------------
    private void showSavedState(String name, String dept, String start, String end,
                                String days, String prac, String lec, int slots) {

        lblSavedName.setText("Institute: " + name);
        lblSavedDept.setText("Department: " + dept);
        lblSavedTime.setText("Time: " + start + " - " + end);
        lblSavedDays.setText("Working Days: " + days);
        lblSavedDurations.setText("Lecture: " + lec + " | Practical: " + prac);
        lblSavedWorkingSlots.setText("Total Slots: " + slots);

        savedView.setVisible(true);
        savedView.setManaged(true);

        editForm.setVisible(false);
        editForm.setManaged(false);
    }

    // -----------------------------------
    // LOAD SAVED CONFIG (ON SCREEN LOAD)
    // -----------------------------------
    private void loadInstituteData() {
        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT * FROM institute_config ORDER BY id DESC LIMIT 1";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                // Load breaks
                breakData.clear();
                String breakSql = "SELECT * FROM institute_breaks WHERE institute_id = ?";
                PreparedStatement ps2 = con.prepareStatement(breakSql);
                ps2.setInt(1, rs.getInt("id"));
                ResultSet rs2 = ps2.executeQuery();

                int count = 1;
                while (rs2.next()) {
                    breakData.add(new BreakEntry(
                            count++,
                            rs2.getString("start_time"),
                            rs2.getString("end_time")
                    ));
                }

                showSavedState(
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        String.valueOf(rs.getInt("working_days")),
                        String.valueOf(rs.getInt("practical_duration")),
                        String.valueOf(rs.getInt("lecture_duration")),
                        rs.getInt("working_slots")
                );

            } else {
                // No data → show form
                editForm.setVisible(true);
                editForm.setManaged(true);

                savedView.setVisible(false);
                savedView.setManaged(false);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------
    // EDIT MODE — SHOW FORM WITH EXISTING DATA
    // -----------------------------------
    @FXML
    private void handleEdit() {
        try (Connection con = DBConnection.getConnection()) {

            String sql = "SELECT * FROM institute_config ORDER BY id DESC LIMIT 1";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                instituteNameField.setText(rs.getString("name"));
                departmentNameField.setText(rs.getString("department"));
                startTimeField.setText(rs.getString("start_time"));
                endTimeField.setText(rs.getString("end_time"));
                workingDaysField.setText(String.valueOf(rs.getInt("working_days")));
                lectureDurationField.setText(String.valueOf(rs.getInt("lecture_duration")));
                practicalDurationField.setText(String.valueOf(rs.getInt("practical_duration")));
            }

            editForm.setVisible(true);
            editForm.setManaged(true);

            savedView.setVisible(false);
            savedView.setManaged(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------
    // UTILITIES
    // -----------------------------------
    private String normalizeTimeFormat(String time) {
        time = time.trim().replace(".", ":").toUpperCase();
        time = time.replaceAll("(?i)(AM|PM)", " $1");
        return time.replaceAll("\\s+", " ").trim();
    }

    private void clearFields() {
        instituteNameField.clear();
        departmentNameField.clear();
        startTimeField.clear();
        endTimeField.clear();
        workingDaysField.clear();
        practicalDurationField.clear();
        lectureDurationField.clear();
        breakData.clear();
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // -----------------------------------
    // Inner BreakEntry Class
    // -----------------------------------
    public static class BreakEntry {
        private final javafx.beans.property.IntegerProperty no;
        private final javafx.beans.property.StringProperty start;
        private final javafx.beans.property.StringProperty end;

        public BreakEntry(int no, String start, String end) {
            this.no = new javafx.beans.property.SimpleIntegerProperty(no);
            this.start = new javafx.beans.property.SimpleStringProperty(start);
            this.end = new javafx.beans.property.SimpleStringProperty(end);
        }

        public javafx.beans.property.IntegerProperty noProperty() { return no; }
        public javafx.beans.property.StringProperty startProperty() { return start; }
        public javafx.beans.property.StringProperty endProperty() { return end; }

        public int getNo() { return no.get(); }
        public void setNo(int n) { no.set(n); }

        public String getStart() { return start.get(); }
        public String getEnd() { return end.get(); }
    }
}
