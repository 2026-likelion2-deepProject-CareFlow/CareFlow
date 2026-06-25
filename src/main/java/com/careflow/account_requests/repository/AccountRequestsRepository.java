package com.careflow.account_requests.repository;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.agency.entity.Agencies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccountRequestsRepository extends JpaRepository<AccountRequests, Long> {

    boolean existsByEmail(String email);

    // n+1 문제 방지를 위한 fetch join
    @Query("SELECT aq FROM AccountRequests aq join fetch aq.agency ag WHERE ag.approvalStatus = AgencyStatus.PENDING AND aq.status = AccountRequestsStatus.PENDING")
    List<AccountRequests> findRequestByPendingAgencies();

    @Query("SELECT aq FROM AccountRequests aq join fetch aq.agency ag WHERE aq.agency.id = :agencyId and ag.approvalStatus = AgencyStatus.APPROVED AND aq.status = AccountRequestsStatus.PENDING")
    List<AccountRequests> findRequestByAgencyIdAndApproved(Long agencyId);

    @Query("SELECT aq FROM AccountRequests aq join fetch aq.agency ag WHERE ag.id = :agencyId AND aq.requestsRole = AccountRequestsRole.ENGINEER and aq.status = AccountRequestsStatus.PENDING")
    List<AccountRequests> findRequestByRequestsRoleAndStatus_Pending(Long agencyId);
}
