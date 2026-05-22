package com.nt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.Division;

public interface DivisionRepository extends JpaRepository<Division,Integer>{
}