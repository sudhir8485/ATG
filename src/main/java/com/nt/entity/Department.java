
package com.nt.entity;

import jakarta.persistence.*;

@Entity
@Table(name="department")
public class Department {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private int id;

private String name;

private String code;

private String hod;


/* ===== Getters & Setters ===== */

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

public String getHod() {
return hod;
}

public void setHod(String hod) {
this.hod = hod;
}

}
