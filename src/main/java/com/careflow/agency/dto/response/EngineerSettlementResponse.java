package com.careflow.agency.dto.response;

import com.careflow.settlement.entity.Settlement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 기사 정산 내역 응답 DTO
 * GET /api/agency/engineers/{id}/settlements
 */
@Getter
@Builder
public class EngineerSettlementResponse {

    private Long settlementId;
    private Long requestId;
    // A/S 예약 작업일
    private LocalDate scheduledDate;
    // 작업 총 금액
    private Integer grossAmount;
    // CareFlow 플랫폼 수수료
    private Integer platformFee;
    // 대행사 수수료
    private Integer agencyFee;
    // 기사 실수령액
    private Integer engineerNetAmount;
    // PENDING | PAID | DISPUTED
    private String status;
    private LocalDateTime createdAt;

    public static EngineerSettlementResponse from(Settlement settlement) {
        return EngineerSettlementResponse.builder()
                .settlementId(settlement.getId())
                .requestId(settlement.getAsRequest().getId())
                .scheduledDate(settlement.getAsRequest().getScheduledDate())
                .grossAmount(settlement.getGrossAmount())
                .platformFee(settlement.getPlatformFee())
                .agencyFee(settlement.getAgencyFee())
                .engineerNetAmount(settlement.getEngineerNetAmount())
                .status(settlement.getStatus())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
