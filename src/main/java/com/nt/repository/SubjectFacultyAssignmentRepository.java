package com.nt.repository;

import com.nt.entity.SubjectFacultyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubjectFacultyAssignmentRepository extends JpaRepository<SubjectFacultyAssignment, Integer> {

    List<SubjectFacultyAssignment> findBySubjectId(int subjectId);

    List<SubjectFacultyAssignment> findBySubjectIdIn(List<Integer> subjectIds);

    /** Lab batch assignments (divisionName is set) */
    @Transactional
    void deleteBySubjectIdAndDivisionName(int subjectId, String divisionName);

    /** Theory additional faculty (divisionName = null, batch = null) */
    List<SubjectFacultyAssignment> findBySubjectIdAndDivisionNameIsNullAndBatchIsNull(int subjectId);

    @Transactional
    void deleteBySubjectIdAndDivisionNameIsNullAndBatchIsNull(int subjectId);
}
