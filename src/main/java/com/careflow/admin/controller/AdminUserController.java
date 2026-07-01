package com.careflow.admin.controller;

import com.careflow.admin.dto.request.AdminUserSearchRequest;
import com.careflow.admin.dto.request.AdminUserStatusUpdateRequest;
import com.careflow.admin.dto.response.AdminMemberTrendResponse;
import com.careflow.admin.dto.response.AdminUserKpiResponse;
import com.careflow.admin.dto.response.AdminUserListResponse;
import com.careflow.admin.service.AdminUserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.dto.UserProfileResponse;
import com.careflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 회원 관리 API 컨트롤러
 * 모든 엔드포인트는 ROLE_ADMIN 로그인 상태 전제
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserService userService;

    /**
     * [GET] /api/admin/users?role=&status=&keyword=&page=&size=
     * 회원 목록을 role(미지정 시 CUSTOMER)/status/keyword 조건으로 검색/페이징 조회한다.
     */
    @GetMapping
    public ResponseEntity<AdminUserListResponse> getUsers(
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @ModelAttribute AdminUserSearchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        return ResponseEntity.ok(adminUserService.searchUsers(pageable, request));
    }

    /**
     * [PATCH] /api/admin/users/{userId}/status
     * 회원 계정 상태(ACTIVE/INACTIVE/SUSPENDED)를 변경한다.
     * status 값 자체의 유효성 검사는 UserService.updateUserStatus 내부에서 수행
     */
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        return ResponseEntity.ok(userService.updateUserStatus(userId, request.status()));
    }

    /**
     * [GET] /api/admin/users/kpi?role=
     * 회원 관리 대시보드 KPI(총원/상태별 인원/오늘 신규가입자 수)를 조회한다.
     */
    @GetMapping("/kpi")
    public ResponseEntity<AdminUserKpiResponse> getUserKpi(
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        return ResponseEntity.ok(adminUserService.getUserKpi(role));
    }

    /**
     * [GET] /api/admin/users/member-trend?role=
     * 최근 7일간 일별 신규 가입자 수 추이(라인차트)를 조회한다.
     */
    @GetMapping("/member-trend")
    public ResponseEntity<List<AdminMemberTrendResponse>> getMemberTrend(
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        return ResponseEntity.ok(adminUserService.getMemberTrend(role));
    }

    // 공통: ADMIN 역할 검증
    private void checkAdminRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"ADMIN".equals(userDetails.getRole())) {
            throw new IllegalAccessException("관리자만 접근할 수 있습니다.");
        }
    }
}
