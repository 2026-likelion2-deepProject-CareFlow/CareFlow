package com.careflow.account_requests.repository;

import com.careflow.account_requests.entity.AccountRequests;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRequestsRepository extends JpaRepository<AccountRequests, Long> {
}
