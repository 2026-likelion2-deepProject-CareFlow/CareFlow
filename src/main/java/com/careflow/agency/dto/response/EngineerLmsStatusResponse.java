package com.careflow.agency.dto.response;

import com.careflow.lms.entity.LmsConfirmation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기사 LMS 교육 이수 현황 응답 DTO
 * GET /api/agency/engineers/{id}/lms
 */
@Getter
@Builder
public class EngineerLmsStatusResponse {

    // 당해 연도 전체 필수 이수 완료 여부 (engineer_profiles.is_lms_completed)
    private Boolean isLmsCompleted;
    // 조회 기준 연도
    private Integer currentYear;
    // 이수한 콘텐츠 이력 목록 (당해 연도)
    private List<ConfirmationItem> confirmations;

    @Getter
    @Builder
    public static class ConfirmationItem {
        private Long contentId;
        private String title;
        private String requiredLevel;
        private LocalDateTime confirmedAt;
        private String confirmedVersion;

        public static ConfirmationItem from(LmsConfirmation confirmation) {
            return ConfirmationItem.builder()
                    .contentId(confirmation.getContent().getContentId())
                    .title(confirmation.getContent().getTitle())
                    .requiredLevel(confirmation.getContent().getRequiredLevel().name())
                    .confirmedAt(confirmation.getConfirmedAt())
                    .confirmedVersion(confirmation.getConfirmedVersion())
                    .build();
        }
    }

    public static EngineerLmsStatusResponse of(
            boolean isLmsCompleted,
            int currentYear,
            List<LmsConfirmation> confirmations) {

        List<ConfirmationItem> items = confirmations.stream()
                .map(ConfirmationItem::from)
                .toList();

        return EngineerLmsStatusResponse.builder()
                .isLmsCompleted(isLmsCompleted)
                .currentYear(currentYear)
                .confirmations(items)
                .build();
    }
}
