package com.atg.model;

public class Subject {
    private int id;
    private String name;
    private String type;
    private int hours;
    private String teacher;

    public Subject(int id, String name, String type, int hours, String teacher) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.hours = hours;
        this.teacher = teacher;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getHours() { return hours; }
    public String getTeacher() { return teacher; }
}
