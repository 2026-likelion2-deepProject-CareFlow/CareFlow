package com.careflow.settlement.service;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.dto.EngineerPayoutPageResponse;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사용 "본인 지급 배치 이력" 조회 서비스 — GET /api/engineer/payouts
 */
@Service
@RequiredArgsConstructor
public class EngineerPayoutService {

    private final EngineerPayoutRepository engineerPayoutRepository;

    @Transactional(readOnly = true)
    public Page<EngineerPayoutPageResponse> getMyPayouts(
            CustomUserDetails userDetails, Pageable pageable) throws IllegalAccessException {

        if (!"ENGINEER".equals(userDetails.getRole())) {
            throw new IllegalAccessException("기사 계정만 접근할 수 있습니다.");
        }

        return engineerPayoutRepository
                .findByEngineer_IdOrderByPayoutYearDescPayoutMonthDesc(userDetails.getUserId(), pageable)
                .map(EngineerPayoutPageResponse::from);
    }
}
