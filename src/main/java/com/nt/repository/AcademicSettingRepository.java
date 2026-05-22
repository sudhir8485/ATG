package com.nt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.AcademicSetting;

public interface AcademicSettingRepository extends JpaRepository<AcademicSetting, Integer> {
}
