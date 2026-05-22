package com.nt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.Timeslot;

public interface TimeslotRepository extends JpaRepository<Timeslot,Integer>{
}