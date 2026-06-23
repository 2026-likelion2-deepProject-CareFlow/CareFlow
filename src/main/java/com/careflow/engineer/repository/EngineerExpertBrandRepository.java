package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineerExpertBrandRepository extends JpaRepository<EngineerExpertBrand, Long> {

    List<EngineerExpertBrand> findByEngineer_Id(Long engineerId);

    void deleteByEngineer_Id(Long engineerId);
}