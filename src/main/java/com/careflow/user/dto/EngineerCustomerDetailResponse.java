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
    private List<ApplianceDto> appliances;
    private Long inProgressRequestId;
    private List<AsHistoryDto> asHistory;

    @Getter @Builder
    public static class ApplianceDto {
        private Long applianceId;
        private String brand;
        private String modelName;
        private String categoryName;
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