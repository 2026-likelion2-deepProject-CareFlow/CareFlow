// 파일 경로: src/main/java/com/careflow/engineer/dto/EngineerNavbarResponse.java
package com.careflow.engineer.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerNavbarResponse {
    private String name;
    private String role; // "수리 기사" 
    private String profileImageUrl;
}

