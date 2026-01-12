package com.atg.test;

import com.atg.controller.InstituteConfigLoader;
import com.atg.engine.TimetableEngine;
import com.atg.model.*;

import java.util.List;

public class TimetableEngineTest {

    public static void main(String[] args) {

        // -------------------------------
        // 1️⃣ LOAD CONFIG FROM DATABASE
        // -------------------------------
        InstituteConfigLoader.LoadedInstituteConfig config =
                InstituteConfigLoader.loadLatest();

        if (config == null) {
            System.out.println("❌ No institute configuration found");
            return;
        }

        System.out.println("✅ Config Loaded");
        System.out.println("Days: " + config.workingDays);
        System.out.println("Slots/day: " + config.workingSlots);
        System.out.println("Lecture Duration: " + config.lectureDuration);
        System.out.println("Practical Duration: " + config.practicalDuration);
        System.out.println("Breaks: " + config.breaks.size());

        // -------------------------------
        // 2️⃣ CREATE TEACHERS
        // -------------------------------
        Teacher t1 = new Teacher(1, "Rahul");
        Teacher t2 = new Teacher(2, "Anita");

        // -------------------------------
        // 3️⃣ CREATE SUBJECTS
        // -------------------------------
        Subject maths = new Subject(
                1, "Maths", "THEORY", 4, t1);

        Subject physicsLab = new Subject(
                2, "Physics Lab", "PRACTICAL", 2, t2);

        // -------------------------------
        // 4️⃣ CREATE CLASS
        // -------------------------------
        ClassRoom fybca = new ClassRoom(
                1,
                "FY-BCA",
                List.of(maths, physicsLab)
        );

        // -------------------------------
        // 5️⃣ CREATE ENGINE
        // ⚠ SAME START TIME AS UI
        // -------------------------------
        TimetableEngine engine =
                new TimetableEngine(config, "9:00 AM");

        // -------------------------------
        // 6️⃣ GENERATE TIMETABLE
        // -------------------------------
        Timetable timetable =
                engine.generate(List.of(fybca));

        // -------------------------------
        // 7️⃣ PRINT RESULT
        // -------------------------------
        System.out.println("\n📅 GENERATED TIMETABLE\n");

        for (TimetableEntry e : timetable.getEntries()) {
            System.out.println(
                    "Day " + e.getDay() +
                    " | Slot " + e.getSlot() +
                    " | Class " + e.getClassRoom().getName() +
                    " | Subject " + e.getSubject().getName() +
                    " | Teacher " + e.getTeacher().getName()
            );
        }

        System.out.println("\n✅ Timetable generation SUCCESS");
    }
}
