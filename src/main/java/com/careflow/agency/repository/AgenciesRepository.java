package com.careflow.agency.repository;

import com.careflow.agency.entity.Agencies;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.ScopedValue;
import java.util.Optional;

@Repository
public interface AgenciesRepository extends JpaRepository<Agencies, Long> {

    @Fetch(FetchMode.JOIN)
    Optional<Agencies> findAgenciesByAgencyNameAndBusinessNumber(String agencyName, String businessNumber);

    Optional<Long> findRepresentativeIdById(Long id);
}
