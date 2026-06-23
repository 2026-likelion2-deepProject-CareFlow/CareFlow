package com.careflow.agency.repository;

import com.careflow.agency.entity.Agencies;
import com.careflow.common.enums.AgencyStatus;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgenciesRepository extends JpaRepository<Agencies, Long> {

    @Fetch(FetchMode.JOIN)
    Optional<Agencies> findByBusinessNumber(String businessNumber);

    @Query("SELECT a FROM Agencies a WHERE a.representativeId = :userId")
    Optional<Agencies> findByRepresentativeById(Long userId);

    // 상호명 + 승인 상태로 조회 — PENDING/REJECTED 대행사는 검색 결과에서 제외
    Optional<Agencies> findByAgencyNameAndApprovalStatus(String agencyName, AgencyStatus approvalStatus);
}
