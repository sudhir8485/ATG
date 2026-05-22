
package com.nt.entity;

import jakarta.persistence.*;

@Entity
public class Classroom {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String room;
private String building;
private Integer capacity;
private String type; // Lecture or Lab

public int getId() {
return id;
}

public void setId(int id) {
this.id = id;
}

public String getRoom() {
return room;
}

public void setRoom(String room) {
this.room = room;
}

public String getBuilding(){return building;}
public void setBuilding(String building){this.building=building;}

public Integer getCapacity(){return capacity;}
public void setCapacity(Integer capacity){this.capacity=capacity;}

public String getType(){return type;}
public void setType(String type){this.type=type;}

}
