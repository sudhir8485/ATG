
package com.nt.entity;

import jakarta.persistence.*;

@Entity
public class Subject {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String name;
private String code;
private String department;
private Integer semester;
private Integer credits;
private Integer hours;
private Integer lectureHoursPerWeek;
private Integer practicalHoursPerWeek;
private Integer practicalSlotDuration;
private String type;
private String description;
private String faculty; // assigned faculty name
private boolean deleted = false;

/** Comma-separated days this subject must be pinned to (e.g. "Friday" or "Monday,Thursday").
 *  Blank = no pin, generator places freely. Admin sets this per-semester via UI. */
private String pinDays;

/** Slot start time for the pin (HH:MM, 24-h). Must be set together with pinDays. */
private String pinSlot;

public int getId() {
return id;
}

public void setId(int id) {
this.id = id;
}

public String getName() {
return name;
}

public void setName(String name) {
this.name = name;
}

public String getCode() {
return code;
}

public void setCode(String code) {
this.code = code;
}

public String getDepartment() {
return department;
}

public void setDepartment(String department) {
this.department = department;
}

public Integer getSemester() {
return semester;
}

public void setSemester(Integer semester) {
this.semester = semester;
}

public Integer getCredits() {
return credits;
}

public void setCredits(Integer credits) {
this.credits = credits;
}

public Integer getHours() {
return hours;
}

public void setHours(Integer hours) {
this.hours = hours;
}

public Integer getLectureHoursPerWeek() {
return lectureHoursPerWeek;
}

public void setLectureHoursPerWeek(Integer lectureHoursPerWeek) {
this.lectureHoursPerWeek = lectureHoursPerWeek;
}

public Integer getPracticalHoursPerWeek() {
return practicalHoursPerWeek;
}

public void setPracticalHoursPerWeek(Integer practicalHoursPerWeek) {
this.practicalHoursPerWeek = practicalHoursPerWeek;
}

public Integer getPracticalSlotDuration() {
return practicalSlotDuration;
}

public void setPracticalSlotDuration(Integer practicalSlotDuration) {
this.practicalSlotDuration = practicalSlotDuration;
}

public String getType() {
return type;
}

public void setType(String type) {
this.type = type;
}

public String getDescription() {
return description;
}

public void setDescription(String description) {
this.description = description;
}

public String getFaculty() {
return faculty;
}

public void setFaculty(String faculty) {
this.faculty = faculty;
}

public boolean isDeleted() {
return deleted;
}

public void setDeleted(boolean deleted) {
this.deleted = deleted;
}

public String getPinDays() {
return pinDays;
}

public void setPinDays(String pinDays) {
this.pinDays = pinDays;
}

public String getPinSlot() {
return pinSlot;
}

public void setPinSlot(String pinSlot) {
this.pinSlot = pinSlot;
}

}
