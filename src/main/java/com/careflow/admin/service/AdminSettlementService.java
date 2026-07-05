package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.AdminAgencySettlementProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자용 대행사 정산 관리 서비스
 * - GET /api/admin/settlements (월별 전체 대행사 정산 현황)
 * - GET /api/admin/settlements/{agencyId}/details (건별 내역)
 * - PATCH /api/admin/settlements/{agencyId}/approve (단일 대행사 지급 승인)
 * - PATCH /api/admin/settlements/approve-all (미지급 전체 일괄 승인)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    private final SettlementRepository settlementRepository;
    private final AgenciesRepository agenciesRepository;
    private final PlatformSettlementRepository platformSettlementRepository;
    private final AgencyBankAccountRepository agencyBankAccountRepository;

    /**
     * [⑦] 전체 대행사 대상 월별 정산 현황 조회
     */
    @Transactional(readOnly = true)
    public AdminSettlementSummaryResponse getMonthlySummary(
            CustomUserDetails userDetails, int year, int month) throws IllegalAccessException {

        checkAdminRole(userDetails);
        validateMonth(month);

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        List<AdminAgencySettlementProjection> rows =
                settlementRepository.findAllAgenciesMonthlySummary(from, to);

        // 대행사별 실제 platform_settlements 배치(상태/지급 계좌 스냅샷) 조회 — settlements 집계와 별개로 원본 그대로 노출
        Map<Long, PlatformSettlement> batchByAgencyId = platformSettlementRepository
                .findBySettlementYearAndSettlementMonth(year, month).stream()
                .collect(Collectors.toMap(ps -> ps.getAgency().getId(), Function.identity()));

        List<AdminSettlementSummaryResponse.AgencySettlementItem> items = rows.stream()
                .map(row -> toAgencyItem(row, batchByAgencyId.get(row.getAgencyId())))
                .toList();

        long totalRevenue = items.stream().mapToLong(AdminSettlementSummaryResponse.AgencySettlementItem::totalRevenue).sum();
        long totalCareflowFee = items.stream().mapToLong(AdminSettlementSummaryResponse.AgencySettlementItem::careflowFee).sum();
        long totalAgencyPay = items.stream().mapToLong(AdminSettlementSummaryResponse.AgencySettlementItem::agencyPay).sum();
        long pendingCount = items.stream().filter(i -> "PENDING".equals(i.status())).count();

        AdminSettlementSummaryResponse.Summary summary = new AdminSettlementSummaryResponse.Summary(
                totalRevenue, totalCareflowFee, totalAgencyPay, pendingCount);

        return new AdminSettlementSummaryResponse(summary, items);
    }

    /**
     * [⑧] 특정 대행사의 월별 건별 정산 내역 조회
     */
    @Transactional(readOnly = true)
    public List<AdminSettlementDetailResponse> getAgencyDetails(
            CustomUserDetails userDetails, Long agencyId, int year, int month) throws IllegalAccessException {

        checkAdminRole(userDetails);
        validateAgencyExists(agencyId);
        validateMonth(month);

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        return settlementRepository.findAgencySettlementDetails(agencyId, from, to).stream()
                .map(this::toDetailResponse)
                .toList();
    }

    /**
     * [⑨] 단일 대행사 지급 승인
     * [D 수정] settlements를 직접 markPaid()하던 기존 방식에서 platform_settlements 배치 단위 승인으로 변경.
     * - 대상 월의 platform_settlement이 없으면 승인할 대상 자체가 없다는 뜻이므로 NoSuchElementException
     * - 정산금 수취 계좌(agency_bank_accounts) 미등록 대행사는 지급 불가 (DDL 설계 의도)
     * - platform_settlement.markPaid(계좌ID) 확정 후, 그 배치에 묶인 settlements 전체를 일괄 PAID로 캐스케이드
     * - 이미 PAID인 배치를 재요청해도 에러 없이 종료 (멱등)
     */
    @Transactional
    public void approveAgency(
            CustomUserDetails userDetails, Long agencyId, int year, int month) throws IllegalAccessException {

        checkAdminRole(userDetails);
        validateAgencyExists(agencyId);
        validateMonth(month);

        PlatformSettlement platformSettlement = platformSettlementRepository
                .findByAgency_IdAndSettlementYearAndSettlementMonth(agencyId, year, month)
                .orElseThrow(() -> new NoSuchElementException("해당 기간의 정산 배치가 존재하지 않습니다."));

        approvePlatformSettlement(platformSettlement);
    }

    /**
     * [⑩] 미지급 전체 일괄 승인 — agencyId 필터 없이 대상 월 전체 대행사 대상
     * [D 수정] platform_settlements 배치 단위로 전환. 계좌 미등록 대행사는 개별적으로 건너뛰고
     * (경고 로그만 남김) 나머지 대행사는 계속 처리한다 — 한 대행사의 계좌 미등록이 전체 일괄 승인을 막지 않도록 함.
     */
    @Transactional
    public void approveAll(CustomUserDetails userDetails, int year, int month) throws IllegalAccessException {
        checkAdminRole(userDetails);
        validateMonth(month);

        List<PlatformSettlement> targets = platformSettlementRepository
                .findBySettlementYearAndSettlementMonthAndStatusNot(year, month, "PAID");

        for (PlatformSettlement platformSettlement : targets) {
            try {
                approvePlatformSettlement(platformSettlement);
            } catch (IllegalStateException e) {
                log.warn("[정산 지급 일괄 승인] 계좌 미등록으로 스킵 — agencyId={}, {}년 {}월, 사유={}",
                        platformSettlement.getAgency().getId(), year, month, e.getMessage());
            }
        }
    }

    /**
     * [⑪] 건별 정산 상태 변경 (PATCH /api/admin/settlements/{settlementId}/status)
     * CareFlow→대행사 지급 상태(Settlement.status)는 플랫폼이 대행사에게 줄 돈에 대한 판단이므로,
     * 대행사가 아닌 ADMIN만 보류(DISPUTED)/재검토(PENDING 복귀) 처리를 할 수 있다.
     * PAID 전이는 이 API로 하지 않는다 — {@link #approveAgency}/{@link #approveAll}의 배치 승인 전용.
     * 이미 PAID인 건은 어떤 변경도 불가.
     */
    @Transactional
    public void updateItemStatus(
            CustomUserDetails userDetails, Long settlementId, String status) throws IllegalAccessException {

        checkAdminRole(userDetails);

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 정산 내역입니다."));

        if ("PAID".equals(settlement.getStatus())) {
            throw new IllegalStateException("지급 완료된 정산은 상태를 변경할 수 없습니다.");
        }

        switch (status) {
            case "DISPUTED" -> settlement.dispute();
            case "PENDING"  -> settlement.revertToPending();
            default -> throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + status);
        }
    }

    /**
     * [⑫] 배치 단위 정산 상태 변경 (PATCH /api/admin/settlements/{agencyId}/batch-status?year=&month=)
     * platform_settlements 배치 자체를 CareFlow가 자체 판단(집계 오류 등)으로 보류/재검토 처리한다.
     * 건별 {@link #updateItemStatus}와 달리 이건 그 달 그 대행사의 배치 전체를 잠그는 것.
     * PAID 전이는 이 API로 하지 않으며(approveAgency/approveAll 전용), 이미 PAID인 배치는
     * 실제 송금이 끝난 불변 기록이므로 어떤 변경도 불가.
     */
    @Transactional
    public void updateBatchStatus(
            CustomUserDetails userDetails, Long agencyId, int year, int month, String status) throws IllegalAccessException {

        checkAdminRole(userDetails);
        validateAgencyExists(agencyId);
        validateMonth(month);

        PlatformSettlement platformSettlement = platformSettlementRepository
                .findByAgency_IdAndSettlementYearAndSettlementMonth(agencyId, year, month)
                .orElseThrow(() -> new NoSuchElementException("해당 기간의 정산 배치가 존재하지 않습니다."));

        if ("PAID".equals(platformSettlement.getStatus())) {
            throw new IllegalStateException("지급 완료된 배치는 상태를 변경할 수 없습니다.");
        }

        switch (status) {
            case "DISPUTED" -> platformSettlement.dispute();
            case "PENDING"  -> platformSettlement.revertToPending();
            default -> throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + status);
        }
    }

    /**
     * platform_settlement 1건 지급 승인 처리 (⑨/⑩ 공통 로직)
     * - 이미 PAID면 아무 것도 하지 않고 정상 종료 (멱등)
     * - 계좌 미등록이면 IllegalStateException — approveAll에서는 이 예외를 잡아 해당 건만 스킵
     */
    private void approvePlatformSettlement(PlatformSettlement platformSettlement) {
        if ("PAID".equals(platformSettlement.getStatus())) {
            return;
        }

        Long agencyId = platformSettlement.getAgency().getId();
        AgencyBankAccount bankAccount = agencyBankAccountRepository.findByAgencyId(agencyId)
                .orElseThrow(() -> new IllegalStateException("정산금 수취 계좌가 등록되지 않아 지급할 수 없습니다."));

        LocalDateTime paidAt = LocalDateTime.now();
        platformSettlement.markPaid(bankAccount.getId());
        settlementRepository.markPaidByPlatformSettlementId(platformSettlement.getId(), paidAt);
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────────────────────

    private AdminSettlementSummaryResponse.AgencySettlementItem toAgencyItem(
            AdminAgencySettlementProjection row, PlatformSettlement batch) {
        long asCount = orZero(row.getAsCount());
        long totalRevenue = orZero(row.getTotalRevenue());
        long careflowFee = orZero(row.getCareflowFee());
        long unpaidCount = orZero(row.getUnpaidCount());
        long agencyPay = totalRevenue - careflowFee;

        String status;
        if (asCount == 0) {
            status = "NONE";
        } else if (unpaidCount == 0) {
            status = "PAID";
        } else {
            status = "PENDING";
        }

        // platform_settlements 원본 상태/지급 계좌 스냅샷 — 배치가 아직 생성되지 않았으면 null
        String platformSettlementStatus = batch != null ? batch.getStatus() : null;
        String paidBankAccount = (batch != null && batch.getPaidBankAccountId() != null)
                ? agencyBankAccountRepository.findById(batch.getPaidBankAccountId())
                        .map(AgencyBankAccount::formatBankAccount)
                        .orElse(null)
                : null;

        return new AdminSettlementSummaryResponse.AgencySettlementItem(
                row.getAgencyId(), row.getAgencyName(), asCount, totalRevenue, careflowFee, agencyPay,
                status, platformSettlementStatus, paidBankAccount);
    }

    private AdminSettlementDetailResponse toDetailResponse(Settlement s) {
        String settlementCode = "SET-" + String.format("%03d", s.getId());
        String completedAt = s.getCreatedAt().toLocalDate().toString();
        String applianceName = s.getAsRequest().getAppliance().getCategory().getName();
        String customerName = s.getAsRequest().getCustomer().getName();
        long totalAmount = s.getGrossAmount();
        long careflowFee = s.getPlatformFee();
        long agencyPay = totalAmount - careflowFee;

        return new AdminSettlementDetailResponse(
                s.getId(), settlementCode, completedAt, applianceName, customerName,
                totalAmount, careflowFee, agencyPay, s.getStatus());
    }

    /** ADMIN 역할 검증 */
    private void checkAdminRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"ADMIN".equals(userDetails.getRole())) {
            throw new IllegalAccessException("관리자만 접근할 수 있습니다.");
        }
    }

    /** 대행사 존재 검증 */
    private void validateAgencyExists(Long agencyId) {
        if (!agenciesRepository.existsById(agencyId)) {
            throw new NoSuchElementException("존재하지 않는 대행사입니다.");
        }
    }

    /** month 1~12 범위 검증 */
    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 1~12 사이여야 합니다.");
        }
    }

    /** null 안전 long 변환 */
    private long orZero(Long value) {
        return value != null ? value : 0L;
    }
}
