package com.careflow.engineer.dto;

import com.careflow.settlement.entity.Settlement;
import lombok.Builder;
import lombok.Getter;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class EngineerSettlementPageResponse {
    private Long settlementId;
    private String requestId;
    private String productName;
    private Integer grossAmount;
    private Integer engineerNetAmount;
    private String status;
    private String paidAt;

    public static EngineerSettlementPageResponse from(Settlement s) {
        String dateStr = s.getAsRequest().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return EngineerSettlementPageResponse.builder()
                .settlementId(s.getId())
                .requestId(String.format("AS-%s-%04d", dateStr, s.getAsRequest().getId()))
                .productName(s.getAsRequest().getAppliance().getBrand() + " " + s.getAsRequest().getAppliance().getModelName())
                .grossAmount(s.getGrossAmount())
                .engineerNetAmount(s.getEngineerNetAmount())
                .status(s.getStatus())
                .paidAt(s.getPaidAt() != null ? s.getPaidAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) : "미정")
                .build();
    }
}