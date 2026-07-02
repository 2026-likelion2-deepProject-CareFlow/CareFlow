package com.careflow.admin.service;

import com.careflow.admin.dto.request.AdminUserSearchRequest;
import com.careflow.admin.dto.response.AdminMemberTrendResponse;
import com.careflow.admin.dto.response.AdminUserKpiResponse;
import com.careflow.admin.dto.response.AdminUserListResponse;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 관리자 화면(회원 관리) 전용 조회/집계 서비스 — 회원 상태 변경(updateStatus)은 UserService가 담당
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MEMBER_TREND_DAYS = 7;

    /**
     * 관리자용 회원 목록 검색 — role(미지정 시 CUSTOMER)/status/keyword 필터, 페이징
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse searchUsers(Pageable pageable, AdminUserSearchRequest request) {
        Role role = parseRole(request != null ? request.role() : null);
        String status = blankToNull(request != null ? request.status() : null);
        String keyword = blankToNull(request != null ? request.keyword() : null);

        Page<User> page = userRepository.searchCustomersForAdmin(role, status, keyword, pageable);
        return AdminUserListResponse.of(page);
    }

    /**
     * 회원 관리 KPI 요약 — role(미지정 시 CUSTOMER) 기준 총원/상태별 인원/오늘 신규가입자 수
     */
    @Transactional(readOnly = true)
    public AdminUserKpiResponse getUserKpi(String roleParam) {
        Role role = parseRole(roleParam);

        long totalCount = userRepository.countByRole(role);
        long activeCount = userRepository.countByRoleAndStatus(role, "ACTIVE");
        long inactiveCount = userRepository.countByRoleAndStatus(role, "INACTIVE");
        long suspendedCount = userRepository.countByRoleAndStatus(role, "SUSPENDED");

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long todayNewCount = userRepository.countByRoleAndCreatedAtRange(role, startOfToday, startOfToday.plusDays(1));

        return new AdminUserKpiResponse(totalCount, activeCount, inactiveCount, suspendedCount, todayNewCount);
    }

    /**
     * 최근 7일(오늘 포함) 일별 신규 가입자 수 추이 — role(미지정 시 CUSTOMER) 기준
     */
    @Transactional(readOnly = true)
    public List<AdminMemberTrendResponse> getMemberTrend(String roleParam) {
        Role role = parseRole(roleParam);

        List<AdminMemberTrendResponse> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = MEMBER_TREND_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            long newCount = userRepository.countByRoleAndCreatedAtRange(role, start, start.plusDays(1));
            trend.add(new AdminMemberTrendResponse(date.format(TREND_DATE_FORMATTER), newCount));
        }
        return trend;
    }

    // role 미지정 시 전체 user 기본값 — 잘못된 role 문자열이면 400으로 응답되도록 IllegalArgumentException
    private Role parseRole(String roleParam) {
        if (roleParam == null || roleParam.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(roleParam);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 역할입니다.");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
