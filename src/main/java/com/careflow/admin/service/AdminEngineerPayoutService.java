package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminEngineerPayoutListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 관리자용 대행사→기사 지급 배치 전체 조회 서비스 (분쟁 조정용)
 * GET /api/admin/engineer-payouts
 */
@Service
@RequiredArgsConstructor
public class AdminEngineerPayoutService {

    private final EngineerPayoutRepository engineerPayoutRepository;

    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "PAID", "DISPUTED");

    @Transactional(readOnly = true)
    public AdminEngineerPayoutListResponse getEngineerPayouts(
            CustomUserDetails userDetails, int year, int month, String status) throws IllegalAccessException {

        if (!"ADMIN".equals(userDetails.getRole())) {
            throw new IllegalAccessException("관리자만 접근할 수 있습니다.");
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 1~12 사이여야 합니다.");
        }

        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status 값이 올바르지 않습니다. (허용: PENDING, PAID, DISPUTED)");
        }

        var items = engineerPayoutRepository.findAllByPeriodAndOptionalStatus(year, month, status).stream()
                .map(AdminEngineerPayoutListResponse.Item::from)
                .toList();

        return new AdminEngineerPayoutListResponse(items);
    }

    /**
     * 건별 지급 상태 변경 (PATCH /api/admin/engineer-payouts/{id}/status)
     * 기사가 "지급받지 못했다"는 분쟁을 제기했을 때, CareFlow가 조정자로서 보류(DISPUTED)/재검토(PENDING) 처리한다.
     * 이미 PAID(대행사가 지급 완료로 신고한 건)는 어떤 변경도 불가.
     */
    @Transactional
    public void updateStatus(CustomUserDetails userDetails, Long engineerPayoutId, String status) throws IllegalAccessException {

        if (!"ADMIN".equals(userDetails.getRole())) {
            throw new IllegalAccessException("관리자만 접근할 수 있습니다.");
        }

        EngineerPayout engineerPayout = engineerPayoutRepository.findById(engineerPayoutId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 지급 배치입니다."));

        if ("PAID".equals(engineerPayout.getStatus())) {
            throw new IllegalStateException("지급 완료된 배치는 상태를 변경할 수 없습니다.");
        }

        switch (status) {
            case "DISPUTED" -> engineerPayout.dispute();
            case "PENDING"  -> engineerPayout.revertToPending();
            default -> throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + status);
        }
    }
}
