package com.atg.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.*;
import java.sql.*;
import com.atg.model.Teacher;

public class TeacherController {

    @FXML private TableView<Teacher> teacherTable;
    @FXML private TableColumn<Teacher, Integer> colSrNo;
    @FXML private TableColumn<Teacher, String> colName;
    @FXML private TextField txtName;

    private ObservableList<Teacher> teacherList = FXCollections.observableArrayList();
    private Connection conn;

    @FXML
    public void initialize() {
        connectDB();
        setupTable();
        loadTeachers();
    }

    private void connectDB() {
        try {
        	conn = com.atg.database.DBConnection.getConnection();
            Statement st = conn.createStatement();
            // no AUTOINCREMENT so ids can be reassigned
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS teachers (
                    id INTEGER PRIMARY KEY,
                    name TEXT
                )
            """);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTable() {
        colSrNo.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()).asObject());
        colName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        teacherTable.setItems(teacherList);
    }

    private void loadTeachers() {
        teacherList.clear();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM teachers ORDER BY id")) {
            while (rs.next()) {
                teacherList.add(new Teacher(rs.getInt("id"), rs.getString("name")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void addTeacher() {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            showAlert("Please enter a teacher name!");
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teachers(name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            renumberIds();   // keep ids continuous
            loadTeachers();
            txtName.clear();
            showAlert("Teacher added successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void deleteTeacher() {
        Teacher selected = teacherTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a teacher to delete!");
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM teachers WHERE id = ?")) {
            ps.setInt(1, selected.getId());
            ps.executeUpdate();
            renumberIds();   // re-sequence after deletion
            loadTeachers();
            showAlert("Teacher deleted and IDs reordered!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Reassigns IDs 1..n so there are no gaps */
    private void renumberIds() {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE teachers SET id = (SELECT COUNT(*) FROM teachers t2 WHERE t2.rowid <= teachers.rowid)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setTitle("Info");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
