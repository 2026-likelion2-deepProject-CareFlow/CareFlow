package com.careflow.appliance.repository;

import com.careflow.appliance.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplianceRepository extends JpaRepository<Appliance, Long> {
    List<Appliance> findByOwner_IdOrderByIdDesc(Long ownerId);
}
