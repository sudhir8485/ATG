package com.atg.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class TestSQLite {

    public static void main(String[] args) {

        // Change this path to your actual SQLite DB file:
        String url = "jdbc:sqlite:atg.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            System.out.println("Connected to SQLite database.");

            // SQL command to rename the column
            String sql = "ALTER TABLE institute_config "
                       + "RENAME COLUMN working_hours TO working_slots;";

            stmt.execute(sql);

            System.out.println("Column renamed successfully!");
            System.out.println("working_hours → working_slots");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error while altering column!");
        }
    }
    
}
