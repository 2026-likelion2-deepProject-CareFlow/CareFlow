package com.careflow.part.repository;
import com.careflow.part.domain.entity.RepairPart;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RepairPartRepository extends JpaRepository<RepairPart, Long> {}