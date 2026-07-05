package com.careflow.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class EngineerCustomerDetailResponse {
    private Long customerId;
    private String email;
    private String name;
    private String phone;
    private String region;
    private String addressDetail;
    private String joinedAt;
    private String status;   // 🌟 추가: 고객 계정 상태 (ACTIVE/INACTIVE/SUSPENDED) — users.status

    // ⚠ 아래 4개(grade/preferredContactTime/memo/memos)는 현재 users 스키마에 대응 컬럼이 없어
    //    항상 null / 빈 목록으로 내려간다. 프론트 계약 유지용 placeholder 이며,
    //    실제 값이 필요하면 DB 스키마(등급/선호연락시간/고객메모 컬럼·테이블) 추가 논의가 선행되어야 한다.
    private String grade;                       // 고객 등급(VIP/GOLD 등) — DB 미지원
    private String preferredContactTime;        // 선호 연락 시간 — DB 미지원
    private String memo;                        // 고객 단건 메모 — DB 미지원
    private List<CustomerMemoDto> memos;        // 상담/메모 목록 — DB 미지원(항상 빈 목록)

    private List<ApplianceDto> appliances;

    private Long inProgressRequestId;           // (하위호환) 진행 중 요청 ID — 기존 프론트 카드 유지용
    private InProgressWorkDto inProgressWork;   // 🌟 추가: 진행 중 작업 상세 카드용 객체

    private List<AsHistoryDto> asHistory;

    @Getter @Builder
    public static class ApplianceDto {
        private Long applianceId;
        private String brand;
        private String modelName;
        private String categoryName;
    }

    /** 🌟 진행 중인 작업 카드용 상세 객체 (제품명/증상/방문일/진행상태/상태) */
    @Getter @Builder
    public static class InProgressWorkDto {
        private Long requestId;
        private String productName;    // 브랜드 + 모델명
        private String symptom;        // 증상명
        private String visitDate;      // 방문 예정일 (yyyy-MM-dd)
        private String progressStatus; // 세부 진행상태 (as_status_logs 최신 to_status)
        private String status;         // coarse 상태 (as_requests.status)
    }

    /** 상담/메모 목록용 DTO — 현재 DB 미지원으로 항상 빈 목록. 스키마 추가 시 실제 값 매핑. */
    @Getter @Builder
    public static class CustomerMemoDto {
        private Long memoId;
        private String content;
        private String createdAt;
    }

    @Getter @Builder
    public static class AsHistoryDto {
        private Long reportId;
        private String requestId;
        private String workDate;
        private String productName;
        private String symptom;
        private String diagnosisResult;
        private Integer finalAmount;
    }
}