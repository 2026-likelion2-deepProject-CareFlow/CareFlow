package com.careflow.admin.controller;

import com.careflow.admin.dto.request.BadgeCriteriaDto;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBadgeCriteriaController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AdminBadgeCriteriaController 단위 테스트")
class AdminBadgeCriteriaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private StringRedisTemplate stringRedisTemplate;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor adminAuth;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = new CustomUserDetails(1L, "admin@test.com", "", "ADMIN", null);
        adminAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // Redis의 opsForValue() 모킹
        valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("성공: Redis에 기준이 없으면 기본값(B등급, 75점)을 반환한다.")
    void getCriteria_Default() throws Exception {
        given(valueOperations.get("admin:badge:criteria")).willReturn(null);

        mockMvc.perform(get("/api/admin/badge-criteria").with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minGrade").value("B"))
                .andExpect(jsonPath("$.minScore").value(75));
    }

    @Test
    @DisplayName("성공: ADMIN 권한으로 새로운 인증 기준을 Redis에 저장한다.")
    void updateCriteria_Success() throws Exception {
        BadgeCriteriaDto dto = new BadgeCriteriaDto("A", 90);
        String json = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/api/admin/badge-criteria")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // Redis의 set 메서드가 정상적으로 호출되었는지 검증
        verify(valueOperations).set("admin:badge:criteria", json);
    }
}