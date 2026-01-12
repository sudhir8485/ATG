package com.atg.engine;

import com.atg.model.*;

import java.util.List;

public class ConstraintChecker {

    public static boolean isTeacherFree(
            Teacher teacher, int day, int slot, List<TimetableEntry> entries) {

        return entries.stream().noneMatch(e ->
                e.getTeacher().getId() == teacher.getId()
                && e.getDay() == day
                && e.getSlot() == slot
        );
    }

    public static boolean isClassFree(
            ClassRoom cls, int day, int slot, List<TimetableEntry> entries) {

        return entries.stream().noneMatch(e ->
                e.getClassRoom().getId() == cls.getId()
                && e.getDay() == day
                && e.getSlot() == slot
        );
    }
}
