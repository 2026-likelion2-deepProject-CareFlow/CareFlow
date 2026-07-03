package com.careflow.admin.controller;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.assignment.entity.ExpectedRepairCost;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.Role;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AdminRepairCost 통합 테스트 (H2)")
class AdminRepairCostIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private StringRedisTemplate stringRedisTemplate; // Redis 우회

    @Autowired private UserRepository userRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private ExpectedRepairCostRepository expectedRepairCostRepository;

    private User admin;
    private String adminToken;
    private ExpectedRepairCost savedCost;

    @BeforeEach
    void setUp() {
        // 1. ADMIN 계정 및 토큰 생성
        admin = userRepository.save(User.builder()
                .email("admin@test.com").passwordHash("hash").name("최고관리자").role(Role.ADMIN).build());
        adminToken = jwtProvider.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN", null);

        // 2. 기초 데이터 세팅 (카테고리, 증상)
        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR1").symptomName("소음 발생").build());

        // 3. 수리 비용 가이드 데이터 삽입
        savedCost = expectedRepairCostRepository.save(
                ExpectedRepairCost.createForTest(cat, symptom, 50000, 5)
        );
    }

    @Test
    @DisplayName("성공: 관리자가 수리 비용 가이드를 조회하면 JOIN FETCH를 통해 데이터가 안정적으로 반환된다.")
    void getRepairCosts_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/admin/repair-costs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("냉장고"))
                .andExpect(jsonPath("$[0].symptom").value("소음 발생"))
                .andExpect(jsonPath("$[0].avgCost").value(50000));
    }

    @Test
    @DisplayName("성공: 수리 비용 가이드 금액 수정 시 DB(영속성 컨텍스트)에 즉각 반영된다.")
    void updateRepairCost_Integration_Success() throws Exception {
        Map<String, Integer> requestBody = Map.of("avgCost", 65000);

        mockMvc.perform(patch("/api/admin/repair-costs/" + savedCost.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgCost").value(65000));

        // 실제 DB 값 변경 확인
        ExpectedRepairCost updatedCost = expectedRepairCostRepository.findById(savedCost.getId()).orElseThrow();
        assertThat(updatedCost.getAvgCost()).isEqualTo(65000);
    }
}