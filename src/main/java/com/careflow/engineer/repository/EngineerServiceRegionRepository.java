package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineerServiceRegionRepository extends JpaRepository<EngineerServiceRegion, Long> {

    List<EngineerServiceRegion> findByEngineer_Id(Long engineerId);

    void deleteByEngineer_Id(Long engineerId);
}