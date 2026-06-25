package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.service.AgencyEngineerService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 대행사 관리자 — 소속 기사 관리 API
 *
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하며,
 * 서비스 레이어에서 소속 대행사 일치 여부를 추가 검증한다.
 */
@RestController
@RequestMapping("/api/agencies/me/engineers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencyEngineerController {

    private final AgencyEngineerService agencyEngineerService;

    /**
     * 소속 기사 목록 조회
     * GET /api/agencies/me/engineers
     *
     * 기사 이름 / 전문 카테고리 / 기술 등급 / 활동 지역 / 오늘 근무 상태 / LMS 이수 여부 반환
     */
    @GetMapping
    public ResponseEntity<List<AgencyEngineerSummaryResponse>> getAgencyEngineers(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AgencyEngineerSummaryResponse> response =
                agencyEngineerService.getAgencyEngineers(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 단건 상세 조회
     * GET /api/agencies/me/engineers/{engineerUserId}
     *
     * 경력 정보 / 기술 등급 / 전문 브랜드 / 평점 등 상세 정보 반환
     * 타 대행사 소속 기사 접근 시 401 반환
     */
    @GetMapping("/{engineerUserId}")
    public ResponseEntity<AgencyEngineerDetailResponse> getAgencyEngineerDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId) throws IllegalAccessException {

        AgencyEngineerDetailResponse response =
                agencyEngineerService.getAgencyEngineerDetail(userDetails.getUserId(), engineerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 프로필 수정 (대행사 관리자 권한)
     * PATCH /api/agencies/me/engineers/{engineerUserId}/profile
     *
     * 수정 가능 항목: 전문 가전 카테고리(1개), 전문 브랜드 목록, 활동 지역 목록
     * null로 전달된 필드는 기존값 유지
     */
    @PatchMapping("/{engineerUserId}/profile")
    public ResponseEntity<AgencyEngineerDetailResponse> updateAgencyEngineerProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId,
            @RequestBody AgencyEngineerProfileUpdateRequest request) throws IllegalAccessException {

        AgencyEngineerDetailResponse response =
                agencyEngineerService.updateAgencyEngineerProfile(
                        userDetails.getUserId(), engineerUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 월간 근무표 조회
     * GET /api/agencies/me/engineers/{engineerUserId}/schedules?year=2026&month=6
     *
     * 배차 가용 여부 판단을 위해 기사의 월간 근무 일정 전체 반환
     * 등록된 근무표가 없으면 빈 배열 반환
     */
    @GetMapping("/{engineerUserId}/schedules")
    public ResponseEntity<List<ScheduleResponse>> getAgencyEngineerSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId,
            @RequestParam int year,
            @RequestParam int month) throws IllegalAccessException {

        List<ScheduleResponse> response =
                agencyEngineerService.getAgencyEngineerSchedules(
                        userDetails.getUserId(), engineerUserId, year, month);
        return ResponseEntity.ok(response);
    }
}
