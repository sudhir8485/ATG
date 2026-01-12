package com.atg.model;

import java.util.ArrayList;
import java.util.List;

public class Timetable {
    private final List<TimetableEntry> entries = new ArrayList<>();

    public void add(TimetableEntry entry) {
        entries.add(entry);
    }

    public List<TimetableEntry> getEntries() {
        return entries;
    }
}
