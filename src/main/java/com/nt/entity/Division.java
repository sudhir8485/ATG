package com.nt.entity;

import jakarta.persistence.*;

@Entity
public class Division {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String name;
private String department;
private String year;
private Integer semesterNumber;
private Integer capacity;
private String classroom;
private Integer batchCount;  // e.g. 4 → batches S1,S2,S3,S4
private String batchPrefix;  // e.g. "S" for SE, "T" for TE, "B" for BE
private String labPreference; // MORNING | MIDDAY | AFTERNOON (default)
private Integer studentCount;    // total students in the division
private Integer rollNumberStart; // first roll number for the division
private String classTeacher;     // name of the class teacher for this division

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

public String getDepartment() {
return department;
}

public void setDepartment(String department) {
this.department = department;
}

public String getYear() {
return year;
}

public void setYear(String year) {
this.year = year;
}

public Integer getSemesterNumber() {
return semesterNumber;
}

public void setSemesterNumber(Integer semesterNumber) {
this.semesterNumber = semesterNumber;
}

public Integer getCapacity() {
return capacity;
}

public void setCapacity(Integer capacity) {
this.capacity = capacity;
}

public String getClassroom() {
return classroom;
}

public void setClassroom(String classroom) {
this.classroom = classroom;
}

public Integer getBatchCount() {
	return batchCount;
}

public void setBatchCount(Integer batchCount) {
	this.batchCount = batchCount;
}

public String getBatchPrefix() {
	return batchPrefix;
}

public void setBatchPrefix(String batchPrefix) {
	this.batchPrefix = batchPrefix;
}

public String getLabPreference() {
	return labPreference;
}

public void setLabPreference(String labPreference) {
	this.labPreference = labPreference;
}

public Integer getStudentCount() { return studentCount; }
public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }

public Integer getRollNumberStart() { return rollNumberStart; }
public void setRollNumberStart(Integer rollNumberStart) { this.rollNumberStart = rollNumberStart; }

public String getClassTeacher() { return classTeacher; }
public void setClassTeacher(String classTeacher) { this.classTeacher = classTeacher; }

}
