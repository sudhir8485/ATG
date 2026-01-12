package com.atg.model;

import java.util.List;

public class ClassRoom {
    private int id;
    private String name; // FY-BCA, SY-BCA, etc.
    private List<Subject> subjects;

    public ClassRoom(int id, String name, List<Subject> subjects) {
        this.id = id;
        this.name = name;
        this.subjects = subjects;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public List<Subject> getSubjects() { return subjects; }
}
