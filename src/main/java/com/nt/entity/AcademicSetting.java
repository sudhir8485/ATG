package com.nt.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AcademicSetting {

    @Id
    private Integer id = 1; // singleton row

    // Comma separated working days: e.g., "Monday,Tuesday,Wednesday,Thursday,Friday"
    private String workingDays;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(String workingDays) {
        this.workingDays = workingDays;
    }
}
