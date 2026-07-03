package com.careflow.admin.controller;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.assignment.entity.ExpectedRepairCost;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.symptom.entity.Symptom;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRepairCostController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AdminRepairCostController 단위 테스트")
class AdminRepairCostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ExpectedRepairCostRepository expectedRepairCostRepository;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor adminAuth;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = new CustomUserDetails(1L, "admin@test.com", "", "ADMIN", null);
        adminAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Nested
    @DisplayName("GET /api/admin/repair-costs")
    class GetRepairCosts {
        @Test
        @DisplayName("성공: ADMIN 권한으로 수리 비용 가이드 목록을 조회한다.")
        void success() throws Exception {
            // Mock 데이터 생성
            ApplianceCategory mockCategory = mock(ApplianceCategory.class);
            given(mockCategory.getCategoryId()).willReturn(11);
            given(mockCategory.getName()).willReturn("냉장고");

            Symptom mockSymptom = mock(Symptom.class);
            given(mockSymptom.getSymptomName()).willReturn("냉각 불량");

            ExpectedRepairCost mockCost = ExpectedRepairCost.createForTest(mockCategory, mockSymptom, 85000, 10);

            given(expectedRepairCostRepository.findAllWithCategoryAndSymptom())
                    .willReturn(List.of(mockCost));

            mockMvc.perform(get("/api/admin/repair-costs").with(adminAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].categoryName").value("냉장고"))
                    .andExpect(jsonPath("$[0].symptom").value("냉각 불량"))
                    .andExpect(jsonPath("$[0].avgCost").value(85000));
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/repair-costs/{id}")
    class UpdateRepairCost {
        @Test
        @DisplayName("성공: 특정 수리 비용의 평균 금액을 성공적으로 수정한다.")
        void success() throws Exception {
            // Mock 데이터 생성
            ApplianceCategory mockCategory = mock(ApplianceCategory.class);
            Symptom mockSymptom = mock(Symptom.class);
            ExpectedRepairCost mockCost = ExpectedRepairCost.createForTest(mockCategory, mockSymptom, 85000, 10);

            given(expectedRepairCostRepository.findById(1L)).willReturn(Optional.of(mockCost));

            // 요청 바디 생성 (90,000원으로 수정)
            Map<String, Integer> requestBody = Map.of("avgCost", 90000);

            mockMvc.perform(patch("/api/admin/repair-costs/1")
                            .with(adminAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avgCost").value(90000)); // 수정된 값 반환 확인
        }
    }
}