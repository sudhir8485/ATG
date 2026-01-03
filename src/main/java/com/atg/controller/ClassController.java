package com.atg.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.sql.*;

import com.atg.model.Subject;

public class ClassController {

    @FXML private ComboBox<String> classCombo;
    @FXML private ComboBox<String> semesterCombo;
    @FXML private Label selectedClassLabel;
    @FXML private Label selectedSemesterLabel;

    @FXML private TextField subjectNameField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField hoursField;
    @FXML private ComboBox<String> teachersCombo;

    @FXML private TableView<Subject> subjectTable;
    @FXML private TableColumn<Subject, String> colName;
    @FXML private TableColumn<Subject, String> colType;
    @FXML private TableColumn<Subject, Integer> colHours;
    @FXML private TableColumn<Subject, String> colTeacher;
    @FXML private TableColumn<Subject, Void> colDelete;

    // Popup
    @FXML private StackPane popupContainer;
    @FXML private TextField classField;
    @FXML private ComboBox<String> deleteClassCombo;

    private Connection conn;

    public void initialize() {
        conn = com.atg.database.DBConnection.getConnection();
        
        createTables();
        setupSubjectTable();
        loadClasses();
        loadTeachers();
        enableTeacherSearch();

        semesterCombo.setItems(FXCollections.observableArrayList("Sem-1", "Sem-2"));
        typeCombo.setItems(FXCollections.observableArrayList("Lecture", "Practical"));
    }

    private void createTables() {
        try (Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS classes(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS subject(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT,
                    type TEXT,
                    hours INTEGER,
                    semester TEXT,
                    class_id INTEGER,
                    teacher_id INTEGER
                )
            """);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    /* POPUP METHODS */
    @FXML private void showPopup() {
        popupContainer.setVisible(true);
        popupContainer.setManaged(true);
    }

    @FXML private void closePopup() {
        popupContainer.setVisible(false);
        popupContainer.setManaged(false);
        classField.clear();
        deleteClassCombo.setValue(null);
    }

    @FXML private void popupAddClass() {
        String name = classField.getText().trim();
        if (name.isEmpty()) return;

        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO classes(name) VALUES(?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            classField.clear();
            loadClasses();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void popupDeleteClass() {

        String selected = deleteClassCombo.getValue();

        if (selected == null) {
            showAlert("Warning", "Please select a class to delete.");
            return;
        }

        try {
            int id = getId("classes", selected);

            if (id == -1) {
                showAlert("Error", "Class not found in database.");
                return;
            }

            PreparedStatement ps1 =
                conn.prepareStatement("DELETE FROM subject WHERE class_id=?");
            ps1.setInt(1, id);
            ps1.executeUpdate();

            PreparedStatement ps2 =
                conn.prepareStatement("DELETE FROM classes WHERE id=?");
            ps2.setInt(1, id);

            int rows = ps2.executeUpdate();

            if (rows > 0) {
                showAlert("Success", "Class deleted successfully.");
            } else {
                showAlert("Error", "Delete failed. No rows affected.");
            }

            loadClasses();
            deleteClassCombo.setValue(null);

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Database error occurred.");
        }
    }

    /* Teacher search */
    private void enableTeacherSearch() {
        teachersCombo.setEditable(true);
        teachersCombo.getEditor().textProperty().addListener((obs, oldV, newV) -> filterTeachers(newV));
    }

    private void filterTeachers(String text) {
        ObservableList<String> all = FXCollections.observableArrayList();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM teachers")) {

            while (rs.next()) {
                String name = rs.getString("name");
                if (name.toLowerCase().contains(text.toLowerCase())) {
                    all.add(name);
                }
            }
            teachersCombo.setItems(all);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    /* Subject table setup */
    private void setupSubjectTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colHours.setCellValueFactory(new PropertyValueFactory<>("hours"));
        colTeacher.setCellValueFactory(new PropertyValueFactory<>("teacher"));
        addDeleteButton();
    }

    private void addDeleteButton() {
        colDelete.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Delete");

            {
                btn.setOnAction(e -> {
                    Subject subject = getTableView().getItems().get(getIndex());
                    deleteSubject(subject.getId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private void deleteSubject(int id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM subject WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadSubjects();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /* Loaders */
    private void loadClasses() {
        ObservableList<String> list = FXCollections.observableArrayList();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM classes")) {
            while (rs.next()) list.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }

        classCombo.setItems(list);
        deleteClassCombo.setItems(list);
    }

    private void loadTeachers() {
        ObservableList<String> list = FXCollections.observableArrayList();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM teachers")) {
            while (rs.next()) list.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
        teachersCombo.setItems(list);
    }

    @FXML
    private void selectClass() {
        selectedClassLabel.setText(classCombo.getValue());
        loadSubjects();
    }

    @FXML
    private void selectSemester() {
        selectedSemesterLabel.setText(semesterCombo.getValue());
        loadSubjects();
    }

    @FXML
    private void addSubject() {
        try {
            int classId = getId("classes", classCombo.getValue());
            int teacherId = getId("teachers", teachersCombo.getValue());

            PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO subject(name, type, hours, semester, class_id, teacher_id)
                VALUES(?,?,?,?,?,?)
            """);

            ps.setString(1, subjectNameField.getText());
            ps.setString(2, typeCombo.getValue());
            ps.setInt(3, Integer.parseInt(hoursField.getText()));
            ps.setString(4, semesterCombo.getValue());
            ps.setInt(5, classId);
            ps.setInt(6, teacherId);

            ps.executeUpdate();

            subjectNameField.clear();
            hoursField.clear();
            loadSubjects();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSubjects() {
        ObservableList<Subject> list = FXCollections.observableArrayList();
        try {
            int classId = getId("classes", classCombo.getValue());

            PreparedStatement ps = conn.prepareStatement("""
                SELECT s.id, s.name, s.type, s.hours, t.name AS teacher_name
                FROM subject s
                JOIN teachers t ON t.id = s.teacher_id
                WHERE s.class_id=? AND s.semester=?
            """);

            ps.setInt(1, classId);
            ps.setString(2, semesterCombo.getValue());

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Subject(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("hours"),
                        rs.getString("teacher_name")
                ));
            }

            subjectTable.setItems(list);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private int getId(String table, String name) throws SQLException {

        if (name == null) return -1;

        PreparedStatement ps = conn.prepareStatement(
            "SELECT id FROM " + table + " WHERE TRIM(name) = TRIM(?)"
        );

        ps.setString(1, name.trim());
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

        return -1;
    }


    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
