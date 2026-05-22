package com.nt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.ChangeRequest;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest,Integer>{
}
