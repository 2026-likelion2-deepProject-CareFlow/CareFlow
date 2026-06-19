package com.careflow.agency.repository;

import com.careflow.agency.entity.Agencies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgenciesRepository extends JpaRepository<Agencies, Long> {
    Optional<Agencies> findAgenciesByAgencyNameAndBusinessNumber(String agencyName, String businessNumber);
}
