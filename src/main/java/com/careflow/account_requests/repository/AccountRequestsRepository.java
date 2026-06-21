package com.careflow.account_requests.repository;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.agency.entity.Agencies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccountRequestsRepository extends JpaRepository<AccountRequests, Long> {

    // n+1 문제 방지를 위한 fetch join
    @Query("SELECT aq FROM AccountRequests aq join fetch Agencies ag WHERE aq.agencyId = ag and ag.approvalStatus = 'PENDING'")
    List<AccountRequests> findRequestByPendingAgencies();

    @Query("SELECT aq FROM AccountRequests aq join fetch Agencies ag WHERE aq.agencyId = :agencyId and ag.approvalStatus = 'APPROVED'")
    List<AccountRequests> findRequestByAgencyIdAndApproved(Long agencyId);
}
