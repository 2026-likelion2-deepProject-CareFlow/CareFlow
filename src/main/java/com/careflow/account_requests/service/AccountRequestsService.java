package com.careflow.account_requests.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountRequestsService {

    private final AccountRequestsRepository accountRequestsRepository;

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByPendingAgencies() {
        return accountRequestsRepository.findRequestByPendingAgencies();
    }

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByAgencyIdAndApproved(Long agencyId) {
        return accountRequestsRepository.findRequestByAgencyIdAndApproved(agencyId);
    }
}
