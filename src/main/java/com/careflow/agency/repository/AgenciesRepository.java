package com.careflow.agency.repository;

import com.careflow.agency.entity.Agencies;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgenciesRepository extends JpaRepository<Agencies, Long> {

    @Fetch(FetchMode.JOIN)
    Optional<Agencies> findAgenciesByNameAndBusinessNumber(String agencyName, String businessNumber);

    Optional<Long> findRepresentativeId(Long userId);

    @Query("SELECT a FROM Agencies a WHERE a.representativeId = :userId")
    Optional<Agencies> findByRepresentativeById(Long userId);
}
