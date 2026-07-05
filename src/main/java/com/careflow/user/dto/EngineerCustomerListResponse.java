package com.careflow.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerCustomerListResponse {
    private Long customerId;
    private String name;
    private String phone;
    private String region;
    private String status;
    private Integer appliancesCount;
    private Integer totalAsCount;
    private String lastWorkDate;
}