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

    @Query("SELECT a FROM Agencies a WHERE a.representativeId.id = :userId")
    Optional<Agencies> findByRepresentativeById(Long userId);

    // 상호명 일부 + 승인 상태로 조회 — "한솔"만 입력해도 "한솔전자서비스"가 검색되도록 부분 일치(LIKE) 처리
    // PENDING/REJECTED 대행사는 검색 결과에서 제외. 여러 건이 일치할 수 있어 findFirst + 정렬로 단일 결과 확정
    Optional<Agencies> findFirstByAgencyNameContainingAndApprovalStatusOrderById(String agencyName, AgencyStatus approvalStatus);
}
