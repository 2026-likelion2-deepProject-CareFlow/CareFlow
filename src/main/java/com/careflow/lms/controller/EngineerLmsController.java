package com.careflow.lms.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.lms.dto.LmsContentWithStatusDto;
import com.careflow.lms.service.LmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 기사용 LMS API — 프론트 URI 규약(/api/engineer/lms) 대응 컨트롤러
 *
 * LmsController(/api/lms)는 기사·관리자 공용 경로를 담당하고,
 * 이 컨트롤러는 프론트가 /api/engineer/lms/** 경로로 요청하는 기사 전용 액션을 담당한다.
 */
@RestController
@RequestMapping("/api/engineer/lms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerLmsController {

    private final LmsService lmsService;

    /**
     * [기사] 이수 대상 LMS 콘텐츠 목록 조회
     * GET /api/engineer/lms
     *
     * 본인 category_id + skill_level 기준으로 필터링하여 반환
     * 각 콘텐츠에 당해 연도 이수 여부(completed) 포함
     */
    @GetMapping
    public ResponseEntity<List<LmsContentWithStatusDto>> getMyContents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<LmsContentWithStatusDto> result =
                lmsService.getRequiredContentsWithStatus(userDetails.getUserId());
        return ResponseEntity.ok(result);
    }

    /**
     * [기사] LMS 콘텐츠 이수 처리
     * POST /api/engineer/lms/{contentId}/confirm
     *
     * 이수 처리 후 전체 완료 판정 → engineer_profiles.is_lms_completed 자동 갱신
     */
    @PostMapping("/{contentId}/confirm")
    public ResponseEntity<Void> confirmContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        lmsService.completeContent(userDetails.getUserId(), contentId);
        return ResponseEntity.ok().build();
    }
}
