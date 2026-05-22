package com.nt.entity;

import jakarta.persistence.*;

@Entity
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private Integer timetableId;
    private String faculty;
    private String message;
    private String status; // Pending / Approved / Rejected

    public int getId() {return id;}
    public void setId(int id) {this.id = id;}

    public Integer getTimetableId() {return timetableId;}
    public void setTimetableId(Integer timetableId) {this.timetableId = timetableId;}

    public String getFaculty() {return faculty;}
    public void setFaculty(String faculty) {this.faculty = faculty;}

    public String getMessage() {return message;}
    public void setMessage(String message) {this.message = message;}

    public String getStatus() {return status;}
    public void setStatus(String status) {this.status = status;}
}
