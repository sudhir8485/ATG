
package com.nt.entity;

import jakarta.persistence.*;

@Entity
public class Timeslot {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String startTime;
private String endTime;
private boolean isBreak;

public int getId() {
return id;
}

public void setId(int id) {
this.id = id;
}

public String getStartTime() {
return startTime;
}

public void setStartTime(String startTime) {
this.startTime = startTime;
}

public String getEndTime() {
return endTime;
}

public void setEndTime(String endTime) {
this.endTime = endTime;
}

public boolean isBreak() {
return isBreak;
}

public void setBreak(boolean isBreak) {
this.isBreak = isBreak;
}

}
