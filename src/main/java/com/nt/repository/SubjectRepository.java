package com.nt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject,Integer>{
    java.util.List<Subject> findByDepartment(String department);
    java.util.List<Subject> findByDepartmentContainingIgnoreCase(String department);
    java.util.List<Subject> findByDeletedFalse();
    java.util.List<Subject> findByDepartmentAndDeletedFalse(String department);
    java.util.List<Subject> findByDeletedTrue();
}
