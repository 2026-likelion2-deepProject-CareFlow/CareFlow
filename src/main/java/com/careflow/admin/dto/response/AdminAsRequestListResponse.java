package com.careflow.admin.dto.response;

import com.careflow.as_request.entity.AsRequest;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public record AdminAsRequestListResponse(
        Map<String, Long> stats,
        List<AdminAsRequestItem> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public record AdminAsRequestItem(
            Long requestId,
            String requestCode,
            String customerName,
            String applianceName,
            String symptom,
            String region,
            String status,
            String createdAt
    ) {
        public static AdminAsRequestItem from(AsRequest r) {
            // 접수번호 포맷팅 (예: AS-2026-0891)
            int year = r.getCreatedAt().getYear();
            String requestCode = String.format("AS-%d-%04d", year, r.getId());

            return new AdminAsRequestItem(
                    r.getId(),
                    requestCode,
                    r.getCustomer().getName(),
                    r.getAppliance().getCategory().getName(), // 혹은 getBrand() + " " + getModelName() 등 기획에 맞게
                    r.getSymptom().getSymptomName(),
                    r.getVisitRegion().getName(),
                    r.getStatus().name(),
                    r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            );
        }
    }
}