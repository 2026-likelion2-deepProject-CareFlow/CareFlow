package com.careflow.appliance.repository;

import com.careflow.appliance.entity.ApplianceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceCategoryRepository extends JpaRepository<ApplianceCategory, Integer> {

}
