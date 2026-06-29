package com.careflow.agency.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerRankResponse {

    private int rank;
    private Long engineerUserId;
    private String name;
    private long completedCount;
}
