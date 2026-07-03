package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminAsRequestListResponse;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.AsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAsRequestService {

    private final AsRequestRepository asRequestRepository;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    /**
     * [1] 실시간 A/S 현황 통계 (필터 없는 전체 통계)
     * GET /api/admin/as-requests/stats
     */
    public Map<String, Long> getRealTimeStats() {
        // 지역, 날짜 조건 없이 전체를 집계
        List<Object[]> rows = asRequestRepository.countGroupByStatusForAdmin(null, null, null);
        return convertStatsToMap(rows);
    }

    /**
     * [2] 전체 A/S 처리 내역 페이징 및 필터 검색
     * GET /api/admin/as-requests
     */
    public AdminAsRequestListResponse searchAsRequests(
            String statusStr, String region, String fromDate, String toDate, Pageable pageable) {

        // 1. 파라미터 안전 파싱
        AsStatus status = parseStatus(statusStr);
        LocalDateTime startOfDay = parseDate(fromDate, false);
        LocalDateTime endOfDay = parseDate(toDate, true);

        // 빈 문자열 방어 로직
        String effectiveRegion = (region != null && region.isBlank()) ? null : region;

        // 2. 동적 쿼리 기반 데이터 조회
        Page<AsRequest> page = asRequestRepository.searchAllForAdmin(
                status, effectiveRegion, startOfDay, endOfDay, pageable);

        // 3. 현재 필터(날짜, 지역)가 반영된 통계(stats) 별도 조회
        // 상태값(status) 필터는 통계에 반영하지 않아야, 다른 탭(상태)의 카운트도 볼 수 있음
        List<Object[]> statsRows = asRequestRepository.countGroupByStatusForAdmin(
                effectiveRegion, startOfDay, endOfDay);
        Map<String, Long> statsMap = convertStatsToMap(statsRows);

        // 4. DTO 매핑
        List<AdminAsRequestListResponse.AdminAsRequestItem> content = page.getContent().stream()
                .map(AdminAsRequestListResponse.AdminAsRequestItem::from)
                .toList();

        return new AdminAsRequestListResponse(
                statsMap,
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    // --- 내부 헬퍼 메서드 ---

    private Map<String, Long> convertStatsToMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        // 프론트 명세에 나온 기본 키값 0으로 초기화
        for (AsStatus s : AsStatus.values()) {
            map.put(s.name(), 0L);
        }
        // 조회된 쿼리 결과로 덮어쓰기
        for (Object[] row : rows) {
            AsStatus st = (AsStatus) row[0];
            Long count = (Long) row[1];
            map.put(st.name(), count);
        }
        return map;
    }

    private AsStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return AsStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 A/S 상태값입니다: " + statusStr);
        }
    }

    private LocalDateTime parseDate(String dateStr, boolean isEndDate) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
            // 종료일인 경우 당일 23:59:59.999까지 포함하기 위해 다음날 00:00:00으로 설정 ( < 연산자 사용)
            return isEndDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다 (yyyy-MM-dd): " + dateStr);
        }
    }
}