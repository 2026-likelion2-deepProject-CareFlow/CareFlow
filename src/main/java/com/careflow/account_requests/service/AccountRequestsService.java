package com.careflow.account_requests.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 계정 생성 요청 공통 헬퍼 서비스.
 * 조회 메서드와 승인 시 User 생성·연결 공통 로직을 제공한다.
 * 대행사/수리기사별 승인·거부 비즈니스 로직은 각 전용 서비스에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class AccountRequestsService {

    private final AccountRequestsRepository accountRequestsRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByPendingAgencies() {
        return accountRequestsRepository.findRequestByPendingAgencies();
    }

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByAgencyIdAndApproved(Long agencyId) {
        return accountRequestsRepository.findRequestByAgencyIdAndApproved(agencyId);
    }

    @Transactional(readOnly = true)
    public AccountRequests findRequestById(Long accountId) {
        return accountRequestsRepository.findById(accountId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByRoleAndStatus(Long agencyId) {
        return accountRequestsRepository.findRequestByRequestsRoleAndStatus_Pending(agencyId);
    }

    /**
     * 요청 승인 공통 처리 — User 생성 후 요청·대행사 상태를 갱신한다.
     * AgencyAccountRequestService, EngineerAccountRequestService 양쪽에서 호출한다.
     */
    @Transactional
    public void createUserFromRequest(CustomUserDetails userDetails, AccountRequests accountRequests, Role role) {

        User user = User.builder()
                .agency(accountRequests.getAgency())
                .name(accountRequests.getName())
                .email(accountRequests.getEmail())
                .passwordHash(accountRequests.getPassword()) // 요청 저장 시점에 이미 해싱됨
                .phone(accountRequests.getPhone())
                .role(role)
                .addressDetail(accountRequests.getAddressDetail())
                .regionId(accountRequests.getRegion())
                .build();

        // 회원 정보 저장
        Long userId = userService.saveUser(user);
        User approvedBy = userService.findById(userDetails.getUserId());
        User createdUser = userService.findById(userId);

        // 요청 승인 처리 — 도메인 메서드로 더티 체킹
        accountRequests.approve(approvedBy, createdUser);

        /*
            대행사 정보가 승인 대기중인 상태(PENDING) 이면 슈퍼 계정 생성 요청
            대행사 정보가 승인 되어있는 상태라면(APPROVED) 일반 관리자 계정 요청(대행사 정보 갱신 생략)
            수리기사는 PENDING 상태인 대행사에 대해 계정 생성 요청 불가
         */
        Agencies agencies = accountRequests.getAgency();
        if (agencies.getApprovalStatus() == AgencyStatus.PENDING) {
            // 대행사 상태 업데이트 및 슈퍼 계정 연결 — 도메인 메서드로 더티 체킹
            agencies.approve(approvedBy, createdUser);
        }
    }
}
