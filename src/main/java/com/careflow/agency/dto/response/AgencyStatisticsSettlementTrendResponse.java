package com.careflow.agency.dto.response;

public record AgencyStatisticsSettlementTrendResponse(
        String date,               // yyyy-MM-dd
        long count,
        long grossAmount,
        long platformFee,
        long agencyFee,
        long engineerNetAmount
) {}
