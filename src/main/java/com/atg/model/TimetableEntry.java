package com.atg.model;

public class TimetableEntry {
    private int day;
    private int slot;
    private ClassRoom classRoom;
    private Subject subject;
    private Teacher teacher;

    public TimetableEntry(int day, int slot, ClassRoom classRoom, Subject subject) {
        this.day = day;
        this.slot = slot;
        this.classRoom = classRoom;
        this.subject = subject;
        this.teacher = subject.getTeacher();
    }

    public int getDay() { return day; }
    public int getSlot() { return slot; }
    public ClassRoom getClassRoom() { return classRoom; }
    public Subject getSubject() { return subject; }
    public Teacher getTeacher() { return teacher; }
}
