package com.nt.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "subject_faculty_assignment")
public class SubjectFacultyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "subject_id", nullable = false)
    private int subjectId;

    @Column(name = "division_name")
    private String divisionName;

    @Column(name = "batch")
    private String batch;

    @Column(name = "faculty_name")
    private String facultyName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSubjectId() { return subjectId; }
    public void setSubjectId(int subjectId) { this.subjectId = subjectId; }

    public String getDivisionName() { return divisionName; }
    public void setDivisionName(String divisionName) { this.divisionName = divisionName; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }
}
