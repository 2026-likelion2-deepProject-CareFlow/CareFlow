package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.ApplianceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceCategoryRepository extends JpaRepository<ApplianceCategory, Long> {

}
