
package com.nt.entity;

import jakarta.persistence.*;

@Entity
@Table(name="timetable")
public class Timetable {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String day;

private String department;

private String className;

private String division;

private String subject;

private String faculty;

private String room;

private String lectureType;

private String timeSlot;

private String breakTime;

private String status;

private boolean deleted = false;

/* ===== GETTERS SETTERS ===== */

public int getId() {
return id;
}

public void setId(int id) {
this.id = id;
}

public String getDay() {
return day;
}

public void setDay(String day) {
this.day = day;
}

public String getDepartment() {
return department;
}

public void setDepartment(String department) {
this.department = department;
}

public String getClassName() {
return className;
}

public void setClassName(String className) {
this.className = className;
}

public String getDivision() {
return division;
}

public void setDivision(String division) {
this.division = division;
}

public String getSubject() {
return subject;
}

public void setSubject(String subject) {
this.subject = subject;
}

public String getFaculty() {
return faculty;
}

public void setFaculty(String faculty) {
this.faculty = faculty;
}

public String getRoom() {
return room;
}

public void setRoom(String room) {
this.room = room;
}

public String getLectureType() {
return lectureType;
}

public void setLectureType(String lectureType) {
this.lectureType = lectureType;
}

public String getTimeSlot() {
return timeSlot;
}

public void setTimeSlot(String timeSlot) {
this.timeSlot = timeSlot;
}

public String getBreakTime() {
return breakTime;
}

public void setBreakTime(String breakTime) {
this.breakTime = breakTime;
}

public String getStatus() {
return status;
}

public void setStatus(String status) {
this.status = status;
}

public boolean isDeleted() {
return deleted;
}

public void setDeleted(boolean deleted) {
this.deleted = deleted;
}

}

