package com.atg.model;

public class Subject {
    private int id;
    private String name;
    private String type;
    private int hours;
    private Teacher teacher;

    public Subject(int id, String name, String type, int hours, Teacher teacher) {
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
    public Teacher getTeacher() { return teacher; }

    // 👇 ADD THIS FOR TABLEVIEW (VERY IMPORTANT)
    public String getTeacherName() {
        return teacher != null ? teacher.getName() : "";
    }
}
