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

}
