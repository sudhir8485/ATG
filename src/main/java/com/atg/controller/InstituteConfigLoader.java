package com.atg.controller;

import com.atg.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InstituteConfigLoader {

    // Simple DTO-like class (no refactor of controller)
    public static class LoadedInstituteConfig {
        public int workingDays;
        public int workingSlots;
        public int lectureDuration;
        public int practicalDuration;
        public List<BreakSlot> breaks = new ArrayList<>();
    }

    public static class BreakSlot {
        public int no;
        public String startTime;
        public String endTime;

        public BreakSlot(int no, String startTime, String endTime) {
            this.no = no;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    // 🔥 THIS METHOD IS WHAT ENGINE WILL USE
    public static LoadedInstituteConfig loadLatest() {

        LoadedInstituteConfig config = new LoadedInstituteConfig();

        try (Connection con = DBConnection.getConnection()) {

            // 1️⃣ Load latest institute_config
            String sql = "SELECT * FROM institute_config ORDER BY id DESC LIMIT 1";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("No institute configuration found");
            }

            int instituteId = rs.getInt("id");

            config.workingDays = rs.getInt("working_days");
            config.workingSlots = rs.getInt("working_slots");
            config.lectureDuration = rs.getInt("lecture_duration");
            config.practicalDuration = rs.getInt("practical_duration");

            // 2️⃣ Load breaks (EXACTLY like controller)
            String breakSql = "SELECT * FROM institute_breaks WHERE institute_id = ?";
            PreparedStatement ps2 = con.prepareStatement(breakSql);
            ps2.setInt(1, instituteId);

            ResultSet brs = ps2.executeQuery();
            while (brs.next()) {
                config.breaks.add(
                        new BreakSlot(
                                brs.getInt("break_no"),
                                brs.getString("start_time"),
                                brs.getString("end_time")
                        )
                );
            }

            return config;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
