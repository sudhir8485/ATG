package com.atg.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {

    private static final String DB_NAME = "atg.db";  // <-- SAME DB USED IN ALL CONTROLLERS
    private static final String DB_URL = "jdbc:sqlite:" + DB_NAME;

    public static Connection getConnection() {
        try {
            File dbFile = new File(DB_NAME);
            System.out.println("📁 Connected DB: " + dbFile.getAbsolutePath());
            Connection conn = DriverManager.getConnection(DB_URL);

            // Ensure institute tables exist
            createInstituteTables(conn);

            return conn;
        } catch (SQLException e) {
            System.out.println("❌ DB ERROR: " + e.getMessage());
            return null;
        }
    }

    private static void createInstituteTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS institute_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                department TEXT,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                working_days INTEGER NOT NULL,
                practical_duration INTEGER,
                lecture_duration INTEGER,
                working_slots REAL
            );
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS institute_breaks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                institute_id INTEGER NOT NULL,
                break_no INTEGER,
                start_time TEXT,
                end_time TEXT,
                FOREIGN KEY (institute_id) REFERENCES institute_config(id) ON DELETE CASCADE
            );
        """);

        System.out.println("✅ Institute tables OK");
    }
    public static void fixDatabaseStructure(Connection conn) {
        try (Statement st = conn.createStatement()) {

            // Remove old broken tables
            st.execute("DROP TABLE IF EXISTS subject");
            st.execute("DROP TABLE IF EXISTS class");

            // Create correct tables
            st.execute("""
                CREATE TABLE classes(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE subject(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT,
                    hours INTEGER,
                    semester TEXT,
                    class_id INTEGER,
                    teacher_id INTEGER
                )
            """);

            System.out.println("✅ Database structure fixed successfully");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
