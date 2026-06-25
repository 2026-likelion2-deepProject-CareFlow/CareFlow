package com.careflow.lms.controller;

import com.careflow.lms.dto.*;
import com.careflow.lms.entity.LmsConfirmation;
import com.careflow.lms.entity.LmsContent;
import com.careflow.lms.service.LmsService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class LmsController {

    private final LmsService lmsService;

    // ─────────────────────────────────────────────
    // 기사용 API
    // ─────────────────────────────────────────────

    /**
     * [기사] 본인 이수 대상 콘텐츠 목록 조회
     * GET /api/lms/contents
     *
     * 본인 category_id + skill_level 기준으로 필터링
     * 각 콘텐츠에 당해 연도 이수 여부(completed) 포함
     */
    @GetMapping("/contents")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<List<LmsContentWithStatusDto>> getMyContents(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                lmsService.getRequiredContentsWithStatus(userDetails.getUserId())
        );
    }

    /**
     * [기사] 콘텐츠 단건 조회 (열람)
     * GET /api/lms/contents/{contentId}
     *
     * 기사가 본문을 읽는 화면에서 호출
     * 이수 처리는 별도 POST로 분리 — 열람과 이수를 명시적으로 구분
     */
    @GetMapping("/contents/{contentId}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<LmsContent> getContent(
            @PathVariable Long contentId
    ) {
        return ResponseEntity.ok(lmsService.getContent(contentId));
    }

    /**
     * [기사] 콘텐츠 이수 처리
     * POST /api/lms/contents/{contentId}/complete
     *
     * 이수 처리 후 전체 완료 판정 → is_lms_completed 자동 갱신
     */
    @PostMapping("/contents/{contentId}/complete")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<Void> completeContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        lmsService.completeContent(userDetails.getUserId(), contentId);
        return ResponseEntity.ok().build();
    }

    /**
     * [기사] 당해 연도 LMS 이수 현황 요약
     * GET /api/lms/status
     *
     * 응답 예시: { totalCount: 2, completedCount: 1, isCompleted: false }
     * 기사 홈 대시보드 배너 + LMS 메뉴 상단 요약 카드에서 사용
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<LmsAnnualStatusDto> getAnnualStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(lmsService.getAnnualStatus(userDetails.getUserId()));
    }

    // ─────────────────────────────────────────────
    // 관리자용 API
    // ─────────────────────────────────────────────

    /**
     * [관리자] 전체 콘텐츠 목록 조회 (비활성 포함)
     * GET /api/lms/admin/contents
     */
    @GetMapping("/admin/contents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LmsContent>> getAllContents() {
        return ResponseEntity.ok(lmsService.getAllContents());
    }

    /**
     * [관리자] 콘텐츠 등록 (M-13)
     * POST /api/lms/admin/contents
     */
    @PostMapping("/admin/contents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LmsContent> createContent(
            @RequestBody LmsContentCreateDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                lmsService.createContent(dto, userDetails.getUserId())
        );
    }

    /**
     * [관리자] 콘텐츠 수정 (M-13)
     * PUT /api/lms/admin/contents/{contentId}
     */
    @PutMapping("/admin/contents/{contentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateContent(
            @PathVariable Long contentId,
            @RequestBody LmsContentUpdateDto dto
    ) {
        lmsService.updateContent(contentId, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * [관리자] 콘텐츠 비활성화 (M-13)
     * PATCH /api/lms/admin/contents/{contentId}/deactivate
     */
    @PatchMapping("/admin/contents/{contentId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateContent(@PathVariable Long contentId) {
        lmsService.deactivateContent(contentId);
        return ResponseEntity.ok().build();
    }

    /**
     * [관리자] 콘텐츠 활성화 (M-13)
     * PATCH /api/lms/admin/contents/{contentId}/activate
     */
    @PatchMapping("/admin/contents/{contentId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateContent(@PathVariable Long contentId) {
        lmsService.activateContent(contentId);
        return ResponseEntity.ok().build();
    }

    /**
     * [관리자] 기사별 이수 현황 조회 (M-14)
     * GET /api/lms/admin/engineers/{engineerUserId}/confirmations?year=2026
     *
     * year 파라미터 없으면 전체 연도 조회
     */
    @GetMapping("/admin/engineers/{engineerUserId}/confirmations")
    @PreAuthorize("hasAnyRole('ADMIN','AGENCY')")
    public ResponseEntity<List<LmsConfirmation>> getEngineerConfirmations(
            @PathVariable Long engineerUserId,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.ok(
                lmsService.getEngineerConfirmations(engineerUserId, year)
        );
    }

    /**
     * [관리자] 필터 기반 콘텐츠 목록 조회 (M-13)
     * GET /api/lms/admin/contents/filter?categoryId=11&requiredLevel=BEGINNER&isActive=true
     *
     * 파라미터 전부 optional — 미입력 시 전체 조회와 동일
     */
    @GetMapping("/admin/contents/filter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LmsContent>> getContentsByFilters(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) LmsContent.RequiredLevel requiredLevel,
            @RequestParam(required = false) Boolean isActive
    ) {
        return ResponseEntity.ok(
                lmsService.getContentsByFilters(categoryId, requiredLevel, isActive)
        );
    }

    /**
     * [대행사] 소속 기사 전체 LMS 이수 현황 목록
     * GET /api/lms/agency/{agencyId}/engineers/lms-status
     */
    @GetMapping("/agency/{agencyId}/engineers/lms-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENCY')")
    public ResponseEntity<List<LmsEngineerStatusDto>> getAgencyEngineersLmsStatus(
            @PathVariable Long agencyId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                lmsService.getAgencyEngineersLmsStatus(agencyId, userDetails.getUserId())
        );
    }
}
