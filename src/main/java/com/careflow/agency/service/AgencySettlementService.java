package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencySettlementSearchRequest;
import com.careflow.agency.dto.request.SettlementStatusUpdateRequest;
import com.careflow.agency.dto.response.AgencySettlementListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgencySettlementService {

    private final SettlementRepository settlementRepository;
    private final BankAccountRepository bankAccountRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 대행사 정산 목록 조회 (GET /api/agency/settlements)
     *
     * 처리 순서:
     * 1. AGENCY 역할 검증
     * 2. 날짜 파싱 (fail-fast — DB 조회 전 잘못된 형식 차단)
     * 3. keyword 분기 — 순수 숫자: settlementId 정확 일치 / 문자: 기사명 LIKE
     * 4. stats 집계 (현재 필터로 조회된 결과 전체 기준, 필터 없으면 전체 기간)
     * 5. content 조회 (필터 + 페이징)
     * 6. bank_accounts 일괄 조회 (N+1 방지)
     * 7. Settlement 엔티티 → DTO 매핑 (period 파생 등)
     */
    @Transactional(readOnly = true)
    public AgencySettlementListResponse getSettlements(
            CustomUserDetails userDetails,
            AgencySettlementSearchRequest filter,
            Pageable pageable) throws IllegalAccessException {

        // 1. AGENCY 역할 검증 — ENGINEER도 agencyId 클레임 보유하므로 명시적 검증 필수
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 계정만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        // 2. 날짜 파싱 (fail-fast)
        LocalDateTime dateFrom = parseDate(filter.getDateFrom(), false);
        LocalDateTime dateTo   = parseDate(filter.getDateTo(), true);

        // 3. keyword 분기 — 숫자이면 정산 ID 검색, 아니면 기사명 LIKE 검색
        String raw = filter.getKeyword();
        String trimmed = (raw != null) ? raw.strip() : null;

        String nameKeyword  = null;
        Long   settlementId = null;

        if (trimmed != null && !trimmed.isBlank()) {
            if (trimmed.matches("\\d+")) {
                // 순수 숫자 → 정산 ID 정확히 일치
                settlementId = Long.parseLong(trimmed);
            } else {
                // 숫자가 아님 → 기사명 부분 일치
                nameKeyword = trimmed;
            }
        }

        // 4. stats 집계 — content 조회와 동일한 필터 조건 적용
        AgencySettlementListResponse.Stats stats = buildStats(
                agencyId, filter.getStatus(), dateFrom, dateTo, nameKeyword, settlementId);

        // 5. content 조회 (필터 + 페이징)
        Page<Settlement> settlementPage = settlementRepository.findAgencySettlements(
                agencyId,
                filter.getStatus(),
                dateFrom,
                dateTo,
                nameKeyword,
                settlementId,
                pageable
        );

        // 6. 현재 페이지 기사 ID 목록으로 bank_accounts 일괄 조회 (N+1 방지)
        List<Long> engineerIds = settlementPage.getContent().stream()
                .map(s -> s.getEngineer().getId())
                .distinct()
                .toList();

        Map<Long, BankAccount> bankAccountMap = bankAccountRepository
                .findByEngineerIdIn(engineerIds)
                .stream()
                .collect(Collectors.toMap(BankAccount::getEngineerId, ba -> ba));

        // 7. 엔티티 → DTO 매핑 (bank account 정보 포함)
        Page<AgencySettlementListResponse.SettlementSummary> summaryPage =
                settlementPage.map(s -> toSummary(s, bankAccountMap.get(s.getEngineer().getId())));

        return AgencySettlementListResponse.of(stats, summaryPage);
    }

    /**
     * stats 집계 — 현재 조회 필터(status/dateFrom/dateTo/keyword)로 조회된 결과 전체 기준
     * - 필터가 하나도 없으면 전체 기간 집계
     * - prevMonthCountDiff/prevMonthGrossDiff(전월 대비)는 필터가 걸리면 의미가 없어지므로,
     *   필터가 전혀 없을 때(=이번 달 뷰)만 별도로 "이번 달 vs 전월"을 집계해 계산하고, 그 외에는 null
     */
    private AgencySettlementListResponse.Stats buildStats(
            Long agencyId, String status, LocalDateTime dateFrom, LocalDateTime dateTo,
            String nameKeyword, Long settlementId) {

        List<Object[]> rows = settlementRepository.findAgencySettlementStatsByFilter(
                agencyId, status, dateFrom, dateTo, nameKeyword, settlementId);

        Object[] r = rows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, 0L} : rows.get(0);

        long count          = ((Number) r[0]).longValue();
        long grossAmount    = ((Number) r[1]).longValue();
        long paidAmount     = ((Number) r[2]).longValue();
        long pendingAmount  = ((Number) r[3]).longValue();
        long disputedAmount = ((Number) r[4]).longValue();

        boolean hasFilter = status != null || dateFrom != null || dateTo != null
                || nameKeyword != null || settlementId != null;

        Long prevMonthCountDiff = null;
        Long prevMonthGrossDiff = null;

        if (!hasFilter) {
            YearMonth thisMonth = YearMonth.now();
            YearMonth prevMonth = thisMonth.minusMonths(1);

            LocalDateTime thisMonthStart = thisMonth.atDay(1).atStartOfDay();
            LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
            LocalDateTime prevMonthStart = prevMonth.atDay(1).atStartOfDay();

            List<Object[]> thisRows = settlementRepository.findAgencySettlementStatsByPeriod(
                    agencyId, thisMonthStart, nextMonthStart);
            List<Object[]> prevRows = settlementRepository.findAgencySettlementStatsByPeriod(
                    agencyId, prevMonthStart, thisMonthStart);

            Object[] t = thisRows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, 0L} : thisRows.get(0);
            Object[] p = prevRows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, 0L} : prevRows.get(0);

            long thisMonthCount = ((Number) t[0]).longValue();
            long thisMonthGross = ((Number) t[1]).longValue();
            long prevMonthCount = ((Number) p[0]).longValue();
            long prevMonthGross = ((Number) p[1]).longValue();

            prevMonthCountDiff = thisMonthCount - prevMonthCount;
            prevMonthGrossDiff = thisMonthGross - prevMonthGross;
        }

        return new AgencySettlementListResponse.Stats(
                grossAmount,
                paidAmount,
                pendingAmount,
                disputedAmount,
                count,
                prevMonthCountDiff,
                prevMonthGrossDiff
        );
    }

    /**
     * Settlement 엔티티 → SettlementSummary DTO 변환
     * - periodStart / periodEnd: createdAt 기준 해당 월의 1일 ~ 마지막 날로 파생
     * - completedCount: 1건 고정 (settlement 1건 = A/S 1건)
     * - payMethod: bank_accounts.pay_method → 한글 레이블 변환 (미등록 시 null)
     * - bankAccount: "은행명 계좌번호" 포맷 (미등록 시 null)
     */
    private AgencySettlementListResponse.SettlementSummary toSummary(Settlement s, BankAccount bankAccount) {
        YearMonth month = YearMonth.from(s.getCreatedAt());
        String periodStart = month.atDay(1).format(PERIOD_FORMATTER);
        String periodEnd   = month.atEndOfMonth().format(PERIOD_FORMATTER);

        // 계좌 미등록 기사는 null 반환 (bank_accounts 레코드 없음)
        String payMethod   = bankAccount != null ? bankAccount.getPayMethod().toLabel() : null;
        String bankAccount_ = bankAccount != null ? bankAccount.formatBankAccount() : null;

        return new AgencySettlementListResponse.SettlementSummary(
                s.getId(),
                "ENGINEER",
                s.getEngineer().getId(),
                s.getEngineer().getName(),
                s.getEngineer().getPhone(),
                s.getAgency().getAgencyName(),
                periodStart,
                periodEnd,
                1,
                s.getGrossAmount(),
                s.getFeeRate(),
                s.getPlatformFee(),
                s.getAgencyFeeRate(),
                s.getAgencyFee(),
                s.getEngineerNetAmount(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getPaidAt(),
                payMethod,
                bankAccount_
        );
    }

    /**
     * 정산 상태 변경 (PATCH /api/agency/settlements/{settlementId}/status)
     *
     * 전이 규칙 ([DDL v11] APPROVED 제거 — 월초 일괄 승인 단계가 상태 전이 없이 바로 지급으로 이어짐):
     *   - PAID 상태이면 어떤 변경도 불가
     *   - 그 외(PENDING↔DISPUTED 간 전이, PENDING/DISPUTED → PAID)는 자유롭게 허용
     * 소속 대행사 검증: 요청자의 agencyId 와 정산의 agencyId 가 일치해야 함
     */
    @Transactional
    public void updateStatus(CustomUserDetails userDetails,
                             Long settlementId,
                             SettlementStatusUpdateRequest request) throws IllegalAccessException {

        // AGENCY 역할 검증
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 계정만 접근할 수 있습니다.");
        }

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 정산 내역입니다."));

        // 소속 대행사 검증 — 타 대행사의 정산 변경 차단
        if (!settlement.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("본인 대행사의 정산만 변경할 수 있습니다.");
        }

        String current = settlement.getStatus();
        String target  = request.status();

        // PAID 상태는 어떤 변경도 불가
        if ("PAID".equals(current)) {
            throw new IllegalStateException("지급 완료된 정산은 상태를 변경할 수 없습니다.");
        }

        // 도메인 메서드로 상태 전이 (더티 체킹으로 UPDATE)
        switch (target) {
            case "PAID"      -> settlement.markPaid();
            case "DISPUTED"  -> settlement.dispute();
            case "PENDING"   -> settlement.revertToPending();
            default -> throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + target);
        }
    }

    /**
     * 날짜 문자열 → LocalDateTime 변환
     * - isEndDate=true: dateTo 포함을 위해 +1일 00:00 으로 변환
     */
    private LocalDateTime parseDate(String dateStr, boolean isEndDate) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            return isEndDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (yyyy-MM-dd): " + dateStr);
        }
    }
}
