package com.careflow.agency.service;

import com.careflow.agency.dto.response.AgencyEngineerPayoutListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 대행사용 "대행사→기사 지급" 관리 서비스
 * - GET /api/agency/engineer-payouts (기사별 지급 대상 목록)
 * - PATCH /api/agency/engineer-payouts/{id}/pay (기사 지급 완료 표시)
 */
@Service
@RequiredArgsConstructor
public class AgencyEngineerPayoutService {

    private final EngineerPayoutRepository engineerPayoutRepository;
    private final BankAccountRepository bankAccountRepository;

    @Transactional(readOnly = true)
    public AgencyEngineerPayoutListResponse getEngineerPayouts(
            CustomUserDetails userDetails, int year, int month, Pageable pageable) throws IllegalAccessException {

        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        Page<EngineerPayout> page = engineerPayoutRepository
                .findByAgency_IdAndPayoutYearAndPayoutMonth(agencyId, year, month, pageable);

        List<Long> engineerIds = page.getContent().stream()
                .map(ep -> ep.getEngineer().getId())
                .distinct()
                .toList();

        Map<Long, BankAccount> bankAccountMap = bankAccountRepository
                .findByEngineerIdIn(engineerIds)
                .stream()
                .collect(Collectors.toMap(BankAccount::getEngineerId, ba -> ba));

        Page<AgencyEngineerPayoutListResponse.Item> itemPage =
                page.map(ep -> toItem(ep, bankAccountMap.get(ep.getEngineer().getId())));

        return AgencyEngineerPayoutListResponse.of(itemPage);
    }

    @Transactional
    public void payEngineerPayout(CustomUserDetails userDetails, Long engineerPayoutId) throws IllegalAccessException {

        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        EngineerPayout engineerPayout = engineerPayoutRepository.findById(engineerPayoutId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 지급 배치입니다."));

        if (!engineerPayout.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("본인 대행사의 지급 배치만 처리할 수 있습니다.");
        }

        if ("PAID".equals(engineerPayout.getStatus())) {
            return; // 이미 처리됨 — 멱등
        }

        engineerPayout.markPaid();
    }

    /**
     * 대행사→기사 지급 건별 상태 변경 (PATCH /api/agency/engineer-payouts/{id}/status)
     * 대행사가 자기 책임 하에 있는 지급 건에 대해 스스로 보류(DISPUTED)를 걸거나(예: 내부 검토 필요),
     * 다시 지급 대기(PENDING)로 되돌릴 수 있다. 이미 PAID된 건은 어떤 변경도 불가.
     * PAID 전이는 이 API로 하지 않는다 — {@link #payEngineerPayout} 전용.
     */
    @Transactional
    public void updateStatus(CustomUserDetails userDetails, Long engineerPayoutId, String status) throws IllegalAccessException {

        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        EngineerPayout engineerPayout = engineerPayoutRepository.findById(engineerPayoutId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 지급 배치입니다."));

        if (!engineerPayout.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("본인 대행사의 지급 배치만 변경할 수 있습니다.");
        }

        if ("PAID".equals(engineerPayout.getStatus())) {
            throw new IllegalStateException("지급 완료된 배치는 상태를 변경할 수 없습니다.");
        }

        switch (status) {
            case "DISPUTED" -> engineerPayout.dispute();
            case "PENDING"  -> engineerPayout.revertToPending();
            default -> throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + status);
        }
    }

    private AgencyEngineerPayoutListResponse.Item toItem(EngineerPayout ep, BankAccount bankAccount) {
        String payMethod = bankAccount != null ? bankAccount.getPayMethod().toLabel() : null;
        String bankAccountText = bankAccount != null ? bankAccount.formatBankAccount() : null;

        return new AgencyEngineerPayoutListResponse.Item(
                ep.getId(),
                ep.getEngineer().getId(),
                ep.getEngineer().getName(),
                ep.getEngineer().getPhone(),
                ep.getNetAmountSum(),
                ep.getCaseCount(),
                ep.getStatus(),
                ep.getPaidAt(),
                payMethod,
                bankAccountText);
    }
}
