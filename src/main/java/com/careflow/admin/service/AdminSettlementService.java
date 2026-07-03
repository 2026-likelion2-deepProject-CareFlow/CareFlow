package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.AdminAgencySettlementProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 관리자용 대행사 정산 관리 서비스
 * - GET /api/admin/settlements (월별 전체 대행사 정산 현황)
 * - GET /api/admin/settlements/{agencyId}/details (건별 내역)
 * - PATCH /api/admin/settlements/{agencyId}/approve (단일 대행사 지급 승인)
 * - PATCH /api/admin/settlements/approve-all (미지급 전체 일괄 승인)
 */
@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    private final SettlementRepository settlementRepository;
    private final AgenciesRepository agenciesRepository;

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

        List<AdminSettlementSummaryResponse.AgencySettlementItem> items = rows.stream()
                .map(this::toAgencyItem)
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
     * - 대상 월의 PAID가 아닌 정산(PENDING/APPROVED/DISPUTED) 전부를 markPaid()로 직접 전이
     * - 대상이 없어도(이미 전부 PAID) 에러 없이 종료 (멱등)
     */
    @Transactional
    public void approveAgency(
            CustomUserDetails userDetails, Long agencyId, int year, int month) throws IllegalAccessException {

        checkAdminRole(userDetails);
        validateAgencyExists(agencyId);
        validateMonth(month);

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        List<Settlement> targets = settlementRepository.findUnpaidByAgencyAndMonth(agencyId, from, to);
        targets.forEach(Settlement::markPaid);
    }

    /**
     * [⑩] 미지급 전체 일괄 승인 — agencyId 필터 없이 대상 월 전체 대행사 대상
     */
    @Transactional
    public void approveAll(CustomUserDetails userDetails, int year, int month) throws IllegalAccessException {
        checkAdminRole(userDetails);
        validateMonth(month);

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        List<Settlement> targets = settlementRepository.findUnpaidByMonth(from, to);
        targets.forEach(Settlement::markPaid);
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────────────────────

    private AdminSettlementSummaryResponse.AgencySettlementItem toAgencyItem(AdminAgencySettlementProjection row) {
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

        return new AdminSettlementSummaryResponse.AgencySettlementItem(
                row.getAgencyId(), row.getAgencyName(), asCount, totalRevenue, careflowFee, agencyPay, status);
    }

    private AdminSettlementDetailResponse toDetailResponse(Settlement s) {
        String settlementId = "SET-" + String.format("%03d", s.getId());
        String completedAt = s.getCreatedAt().toLocalDate().toString();
        String applianceName = s.getAsRequest().getAppliance().getCategory().getName();
        String customerName = s.getAsRequest().getCustomer().getName();
        long totalAmount = s.getGrossAmount();
        long careflowFee = s.getPlatformFee();
        long agencyPay = totalAmount - careflowFee;

        return new AdminSettlementDetailResponse(
                settlementId, completedAt, applianceName, customerName, totalAmount, careflowFee, agencyPay);
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
