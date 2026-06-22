package com.careflow.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegionRequest(

        /*
        일단 지금은 name 으로만 받고
        지도 API 를 통해서 상세 지역 데이터가 어떻게 입력으로 들어오는지 확인되면
        필요시 DTO 구조 변경
         */
        @NotBlank
        @Size(max = 50)
        String name
)
{
}
