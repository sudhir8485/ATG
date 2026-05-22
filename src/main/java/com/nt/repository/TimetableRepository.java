
package com.nt.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nt.entity.Timetable;

public interface TimetableRepository extends JpaRepository<Timetable,Integer>{

List<Timetable> findByDepartmentContainingIgnoreCaseAndDeletedFalse(String department);
List<Timetable> findByClassNameContainingIgnoreCaseAndDeletedFalse(String className);
List<Timetable> findByFacultyContainingIgnoreCaseAndDeletedFalse(String faculty);
List<Timetable> findByFacultyContainingIgnoreCaseOrClassNameContainingIgnoreCaseAndDeletedFalse(String faculty, String className);
List<Timetable> findByDeletedFalse();
List<Timetable> findByDeletedTrue();

}

