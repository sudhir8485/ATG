package com.atg.engine;

import com.atg.controller.InstituteConfigLoader;
import com.atg.model.*;

import java.util.List;
import java.util.Set;

public class TimetableEngine {

    private final int workingDays;
    private final int slotsPerDay;
    private final Set<Integer> blockedSlots;
    private final int practicalSlots;

    public TimetableEngine(
            InstituteConfigLoader.LoadedInstituteConfig config,
            String dayStartTime) {

        this.workingDays = config.workingDays;
        this.slotsPerDay = config.workingSlots;
        this.blockedSlots =
                SlotBlocker.getBlockedSlots(config, dayStartTime);

        this.practicalSlots =
                config.practicalDuration / config.lectureDuration;
    }

    public Timetable generate(List<ClassRoom> classes) {

        Timetable timetable = new Timetable();

        for (ClassRoom cls : classes) {

            for (Subject sub : cls.getSubjects()) {

                if (sub.getType().equalsIgnoreCase("PRACTICAL")) {
                    placePractical(cls, sub, timetable);
                } else {
                    placeTheory(cls, sub, timetable);
                }
            }
        }
        return timetable;
    }

    // ---------------- THEORY ----------------
    private void placeTheory(
            ClassRoom cls, Subject sub, Timetable timetable) {

        int remaining = sub.getHours();

        outer:
        for (int day = 1; day <= workingDays; day++) {
            for (int slot = 1; slot <= slotsPerDay; slot++) {

                if (remaining == 0) break outer;
                if (blockedSlots.contains(slot)) continue;

                if (ConstraintChecker.isClassFree(cls, day, slot, timetable.getEntries())
                        && ConstraintChecker.isTeacherFree(
                        sub.getTeacher(), day, slot, timetable.getEntries())) {

                    timetable.add(new TimetableEntry(day, slot, cls, sub));
                    remaining--;
                }
            }
        }

        if (remaining > 0)
            throw new RuntimeException(
                    "Cannot place theory subject: " + sub.getName());
    }

    // ---------------- PRACTICAL ----------------
    private void placePractical(
            ClassRoom cls, Subject sub, Timetable timetable) {

        for (int day = 1; day <= workingDays; day++) {

            for (int slot = 1;
                 slot <= slotsPerDay - practicalSlots + 1;
                 slot++) {

                boolean possible = true;

                for (int s = 0; s < practicalSlots; s++) {

                    int currentSlot = slot + s;

                    if (blockedSlots.contains(currentSlot)
                            || !ConstraintChecker.isClassFree(
                                cls, day, currentSlot, timetable.getEntries())
                            || !ConstraintChecker.isTeacherFree(
                                sub.getTeacher(), day, currentSlot, timetable.getEntries())) {

                        possible = false;
                        break;
                    }
                }

                if (possible) {
                    for (int s = 0; s < practicalSlots; s++) {
                        timetable.add(
                                new TimetableEntry(
                                        day, slot + s, cls, sub));
                    }
                    return;
                }
            }
        }

        throw new RuntimeException(
                "Cannot place practical subject: " + sub.getName());
    }
}
