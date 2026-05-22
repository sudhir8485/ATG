package com.nt.entity;

import jakarta.persistence.*;

@Entity
@Table(name="users")
public class User {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String name;

@Column(unique = true)
private String username;
private String password;
private String role;
private String department;
private String email;
private String specialization;
private String workingHours;
private String subjectsHandled;
private Integer maxLecturesPerDay;
private Integer maxLecturesPerWeek;
private boolean deleted = false;

public int getId(){return id;}
public void setId(int id){this.id=id;}

public String getName(){return name;}
public void setName(String name){this.name=name;}

public String getUsername(){return username;}
public void setUsername(String username){this.username=username;}

public String getPassword(){return password;}
public void setPassword(String password){this.password=password;}

public String getRole(){return role;}
public void setRole(String role){this.role=role;}

public String getDepartment(){return department;}
public void setDepartment(String department){this.department=department;}

public String getEmail(){return email;}
public void setEmail(String email){this.email=email;}

public String getSpecialization(){return specialization;}
public void setSpecialization(String specialization){this.specialization=specialization;}

public String getWorkingHours(){return workingHours;}
public void setWorkingHours(String workingHours){this.workingHours=workingHours;}

public String getSubjectsHandled(){return subjectsHandled;}
public void setSubjectsHandled(String subjectsHandled){this.subjectsHandled=subjectsHandled;}

public Integer getMaxLecturesPerDay(){return maxLecturesPerDay;}
public void setMaxLecturesPerDay(Integer maxLecturesPerDay){this.maxLecturesPerDay=maxLecturesPerDay;}

public Integer getMaxLecturesPerWeek(){return maxLecturesPerWeek;}
public void setMaxLecturesPerWeek(Integer maxLecturesPerWeek){this.maxLecturesPerWeek=maxLecturesPerWeek;}

public boolean isDeleted(){return deleted;}
public void setDeleted(boolean deleted){this.deleted = deleted;}

}
