package com.careflow.common.security;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("전역 XSS 방어 통합 테스트")
class XssProtectionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RegionRepository regionRepository;

    private Regions savedRegion;

    @BeforeEach
    void setUp() {
        savedRegion = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));
    }

    @Test
    @DisplayName("JSON 바디에 <script> 태그가 섞인 문자열이 오면 400과 방어 메시지를 반환한다")
    void requestBody_withScriptTag_isRejected() throws Exception {
        AgencyCreateRequest req = new AgencyCreateRequest(
                "<script>alert(1)</script>", "xss@test.com", "password123", "010-1234-5678",
                savedRegion.getId(), "상세주소", "111-11-11111", "정상대행사",
                "서울특별시 강남구 테헤란로 1", 5.00
        );

        mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값에 허용되지 않는 스크립트/HTML 태그가 포함되어 있습니다."));
    }

    @Test
    @DisplayName("HTML 태그 문자(<,>)가 없는 일반 텍스트는 정상 통과한다")
    void requestBody_withPlainText_isAccepted() throws Exception {
        AgencyCreateRequest req = new AgencyCreateRequest(
                "홍길동 & Co.", "plain@test.com", "password123", "010-1234-5678",
                savedRegion.getId(), "상세주소", "222-22-22222", "정상대행사2",
                "서울특별시 강남구 테헤란로 1", 5.00
        );

        mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("쿼리 파라미터에 스크립트 태그가 섞이면 400을 반환한다")
    void queryParam_withScriptTag_isRejected() throws Exception {
        mockMvc.perform(get("/api/regions")
                        .param("name", "<img src=x onerror=alert(1)>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
