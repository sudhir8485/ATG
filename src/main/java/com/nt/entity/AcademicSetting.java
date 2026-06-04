package com.nt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AcademicSetting {

    @Id
    private Integer id = 1; // singleton row

    // Comma separated working days: e.g., "Monday,Tuesday,Wednesday,Thursday,Friday"
    private String workingDays;

    // ── Institution ──────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String institutionName;       // two-line name with address (MASTER + CLASS sheets)

    @Column(columnDefinition = "TEXT")
    private String institutionNameShort;  // abbreviated without city (FACULTY + LAB sheets)

    // ── Department / Academic ────────────────────────────────────────────────
    private String departmentName;   // e.g. "Information Technology"
    private String academicYear;     // e.g. "2025-26"
    private String semesterDisplay;  // e.g. "II"  (rendered as "SEM-II" or "Semester: II")
    private String wefDate;          // e.g. "01/01/2026"

    // ── Document Metadata ────────────────────────────────────────────────────
    private String doiDate;          // e.g. "01/02/2025"
    private String revisionNumber;   // e.g. "00"
    private String recordNoMaster;   // e.g. "ACA/R/003A"
    private String recordNoClass;    // e.g. "ACA/R/003B"
    private String recordNoLab;      // e.g. "ACA/R/003D"
    private String recordNoFaculty;  // e.g. "ACA/R/003E"

    // ── Signature Block ──────────────────────────────────────────────────────
    private String ttCoordinatorName; // e.g. "PROF. R. A. NIKAM"
    private String hodSignatureName;  // e.g. "DR. A. A. KADAM"
    private String principalName;     // e.g. "DR. S. B. THAKARE"

    // ── Logo Paths ───────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String collegeLogo;   // absolute path to college logo PNG
    @Column(columnDefinition = "TEXT")
    private String ownerLogo;     // absolute path to owner/institute logo PNG

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

    public String getInstitutionNameShort() { return institutionNameShort; }
    public void setInstitutionNameShort(String institutionNameShort) { this.institutionNameShort = institutionNameShort; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getSemesterDisplay() { return semesterDisplay; }
    public void setSemesterDisplay(String semesterDisplay) { this.semesterDisplay = semesterDisplay; }

    public String getWefDate() { return wefDate; }
    public void setWefDate(String wefDate) { this.wefDate = wefDate; }

    public String getDoiDate() { return doiDate; }
    public void setDoiDate(String doiDate) { this.doiDate = doiDate; }

    public String getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(String revisionNumber) { this.revisionNumber = revisionNumber; }

    public String getRecordNoMaster() { return recordNoMaster; }
    public void setRecordNoMaster(String recordNoMaster) { this.recordNoMaster = recordNoMaster; }

    public String getRecordNoClass() { return recordNoClass; }
    public void setRecordNoClass(String recordNoClass) { this.recordNoClass = recordNoClass; }

    public String getRecordNoLab() { return recordNoLab; }
    public void setRecordNoLab(String recordNoLab) { this.recordNoLab = recordNoLab; }

    public String getRecordNoFaculty() { return recordNoFaculty; }
    public void setRecordNoFaculty(String recordNoFaculty) { this.recordNoFaculty = recordNoFaculty; }

    public String getTtCoordinatorName() { return ttCoordinatorName; }
    public void setTtCoordinatorName(String ttCoordinatorName) { this.ttCoordinatorName = ttCoordinatorName; }

    public String getHodSignatureName() { return hodSignatureName; }
    public void setHodSignatureName(String hodSignatureName) { this.hodSignatureName = hodSignatureName; }

    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }

    public String getCollegeLogo() { return collegeLogo; }
    public void setCollegeLogo(String collegeLogo) { this.collegeLogo = collegeLogo; }

    public String getOwnerLogo() { return ownerLogo; }
    public void setOwnerLogo(String ownerLogo) { this.ownerLogo = ownerLogo; }
}
