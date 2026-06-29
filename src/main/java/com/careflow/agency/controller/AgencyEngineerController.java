package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.dto.response.EngineerLmsStatusResponse;
import com.careflow.agency.dto.response.EngineerRankResponse;
import com.careflow.agency.dto.response.EngineerRealtimeStatusResponse;
import com.careflow.agency.dto.response.EngineerRecommendResponse;
import com.careflow.agency.dto.response.EngineerReviewListResponse;
import com.careflow.agency.dto.response.EngineerSettlementResponse;
import com.careflow.agency.service.AgencyEngineerService;
import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 대행사 관리자 — 소속 기사 관리 API
 *
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하며,
 * 서비스 레이어에서 소속 대행사 일치 여부를 추가 검증한다.
 */
@RestController
@RequestMapping("/api/agency/engineers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencyEngineerController {

    private final AgencyEngineerService agencyEngineerService;

    /**
     * 대행사 소속 기사 수리 완료 실적 TOP 3 조회
     * GET /api/agency/engineers/top3
     *
     * 대행사 대시보드에서 우수 기사를 표시하기 위한 API.
     * as_requests.status = COMPLETED 기준으로 집계하여 건수 내림차순 상위 3명을 반환한다.
     * 소속 기사 수가 3명 미만이면 실제 인원 수만큼만 반환하며, 없으면 빈 배열을 반환한다.
     */
    @GetMapping("/top3")
    public ResponseEntity<List<EngineerRankResponse>> getTop3Engineers(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<EngineerRankResponse> response =
                agencyEngineerService.getTop3Engineers(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

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
     * GET /api/agency/engineers/{engineerUserId}/profile
     *
     * 경력 정보 / 기술 등급 / 전문 브랜드 / 평점 등 상세 정보 반환
     * 타 대행사 소속 기사 접근 시 401 반환
     */
    @GetMapping("/{engineerUserId}/profile")
    public ResponseEntity<AgencyEngineerDetailResponse> getAgencyEngineerDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId) throws IllegalAccessException {

        AgencyEngineerDetailResponse response =
                agencyEngineerService.getAgencyEngineerDetail(userDetails.getUserId(), engineerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 프로필 수정 (대행사 관리자 권한)
     * PUT /api/agency/engineers/{engineerUserId}/profile
     *
     * 수정 가능 항목: 전문 가전 카테고리(1개), 전문 브랜드 목록, 활동 지역 목록
     * null로 전달된 필드는 기존값 유지
     */
    @PutMapping("/{engineerUserId}/profile")
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
     * 소속 기사 A/S 작업 일정 조회
     * GET /api/agency/engineers/{engineerUserId}/schedule?date=2026-06-01
     *
     * 특정 날짜 배정된 작업 목록(고객·제품·방문주소 포함) 반환
     * REJECTED 건 제외, 타 대행사 소속 기사 조회 시 403
     */
    @GetMapping("/{engineerUserId}/schedule")
    public ResponseEntity<List<EngineerTaskScheduleResponse>> getAgencyEngineerTaskSchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws IllegalAccessException {

        List<EngineerTaskScheduleResponse> response =
                agencyEngineerService.getAgencyEngineerTaskSchedule(
                        userDetails.getUserId(), engineerUserId, date);
        return ResponseEntity.ok(response);
    }

    /**
     * A/S 요청 기반 추천 기사 목록 조회
     * GET /api/agency/engineers/recommended?requestId=
     *
     * LMS 이수 완료 + 해당 날짜 AVAILABLE 근무표를 가진 소속 기사를 평점 내림차순으로 반환한다.
     */
    @GetMapping("/recommended")
    public ResponseEntity<List<EngineerRecommendResponse>> getRecommendedEngineers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long requestId) {

        List<EngineerRecommendResponse> response =
                agencyEngineerService.getRecommendedEngineers(userDetails.getUserId(), requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 실시간 배정 현황 조회
     * GET /api/agency/engineers/realtime-status
     *
     * 소속 기사 전원의 현재 배정 상태(IN_PROGRESS / ASSIGNED / null)와 LMS 이수 여부를 반환한다.
     */
    @GetMapping("/realtime-status")
    public ResponseEntity<List<EngineerRealtimeStatusResponse>> getEngineersRealtimeStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<EngineerRealtimeStatusResponse> response =
                agencyEngineerService.getEngineersRealtimeStatus(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 정산 내역 조회
     * GET /api/agency/engineers/{engineerUserId}/settlements
     *
     * 기사별 정산 이력 전체를 최신순으로 반환한다. 타 대행사 소속 기사 접근 시 401 반환.
     */
    @GetMapping("/{engineerUserId}/settlements")
    public ResponseEntity<List<EngineerSettlementResponse>> getEngineerSettlements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId) throws IllegalAccessException {

        List<EngineerSettlementResponse> response =
                agencyEngineerService.getEngineerSettlements(userDetails.getUserId(), engineerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 LMS 이수 현황 조회
     * GET /api/agency/engineers/{engineerUserId}/lms
     *
     * 당해 연도 이수 완료 여부 + 이수 콘텐츠 이력을 반환한다. 타 대행사 소속 기사 접근 시 401 반환.
     */
    @GetMapping("/{engineerUserId}/lms")
    public ResponseEntity<EngineerLmsStatusResponse> getEngineerLmsStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId) throws IllegalAccessException {

        EngineerLmsStatusResponse response =
                agencyEngineerService.getEngineerLmsStatus(userDetails.getUserId(), engineerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 수신 리뷰 목록 조회
     * GET /api/agency/engineers/{engineerUserId}/reviews
     *
     * 공개 리뷰(isVisible=true)만 최신순으로 반환한다. 타 대행사 소속 기사 접근 시 401 반환.
     */
    @GetMapping("/{engineerUserId}/reviews")
    public ResponseEntity<EngineerReviewListResponse> getEngineerReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerUserId) throws IllegalAccessException {

        EngineerReviewListResponse response =
                agencyEngineerService.getEngineerReviews(userDetails.getUserId(), engineerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 기사 월간 근무표 조회
     * GET /api/agency/engineers/{engineerUserId}/schedules?year=2026&month=6
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
