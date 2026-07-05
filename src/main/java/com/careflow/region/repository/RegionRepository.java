package com.careflow.region.repository;

import com.careflow.region.entity.Regions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Regions, Integer> {
    Optional<Regions> findByName(String name);

    List<Regions> findByDepth(int depth);
}
