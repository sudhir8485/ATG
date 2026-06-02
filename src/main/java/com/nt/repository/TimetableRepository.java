
package com.nt.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.nt.entity.Timetable;

public interface TimetableRepository extends JpaRepository<Timetable,Integer>{

List<Timetable> findByDepartmentContainingIgnoreCaseAndDeletedFalse(String department);
List<Timetable> findByClassNameContainingIgnoreCaseAndDeletedFalse(String className);
List<Timetable> findByFacultyContainingIgnoreCaseAndDeletedFalse(String faculty);
List<Timetable> findByFacultyContainingIgnoreCaseOrClassNameContainingIgnoreCaseAndDeletedFalse(String faculty, String className);
List<Timetable> findByDeletedFalse();
List<Timetable> findByDeletedTrue();

@Modifying
@Transactional
@Query("UPDATE Timetable t SET t.faculty = :faculty WHERE t.subject = :subject AND t.division = :division AND t.batch = :batch AND t.deleted = false")
int updateFacultyBySubjectDivisionBatch(@Param("faculty") String faculty,
                                        @Param("subject") String subject,
                                        @Param("division") String division,
                                        @Param("batch") String batch);

}

