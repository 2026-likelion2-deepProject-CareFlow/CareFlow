package com.careflow.agency.dto.response;

import java.util.List;

/**
 * 대행사 기사 로스터 CSV 가져오기 결과 응답 DTO
 */
public record AgencyDataImportResponse(
        int successCount,
        int failCount,
        List<String> errors
) {
}
