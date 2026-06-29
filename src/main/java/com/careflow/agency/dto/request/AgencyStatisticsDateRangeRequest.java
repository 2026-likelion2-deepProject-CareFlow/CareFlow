package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 대행사 통계 API 공통 기간 요청 DTO
 * GET 요청이므로 @ModelAttribute 로 바인딩
 */
public record AgencyStatisticsDateRangeRequest(

        @NotNull(message = "조회 시작일(dateFrom)은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateFrom,

        @NotNull(message = "조회 종료일(dateTo)은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateTo
) {}
