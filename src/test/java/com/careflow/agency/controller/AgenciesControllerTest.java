package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgenciesController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgenciesController 테스트")
class AgenciesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgenciesService agenciesService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    // develop 병합으로 추가된 OAuth2 의존성 — SecurityConfig 생성 및 oauth2Login() 설정에 필요
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    // 유효한 요청 픽스처 — 각 테스트에서 필드만 교체해서 사용
    private AgencyCreateRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new AgencyCreateRequest(
                "홍길동",
                "test@careflow.com",
                "password123",
                "010-1234-5678",
                "서울특별시 강남구",
                "서울특별시 강남구 테헤란로 123",
                "123-45-67890",
                "테스트대행사",
                "서울특별시 강남구 테헤란로 123",
                5.20
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/agencies/signup
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/agencies/signup — 대행사 계정 생성 요청")
    class SignupAgency {

        @Test
        @DisplayName("성공: 최초 대행사 등록(슈퍼 계정 요청) — 201 Created 반환")
        void signup_superAccount_success() throws Exception {
            given(agenciesService.requestAgencyAccount(any())).willReturn(1L);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("성공: 이미 등록된 대행사의 일반 관리자 계정 요청 — 201 Created 반환")
        void signup_managerAccount_success() throws Exception {
            // 대행사가 이미 존재(APPROVED) → 일반 관리자 요청으로 처리, accountRequestId 반환
            given(agenciesService.requestAgencyAccount(any())).willReturn(2L);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("실패: 이미 가입된 이메일 — 400 Bad Request 반환")
        void signup_duplicateEmail_400() throws Exception {
            given(agenciesService.requestAgencyAccount(any()))
                    .willThrow(new IllegalArgumentException("이미 가입된 이메일입니다."));

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 아직 승인 대기 중인 대행사에 대한 추가 요청 — 403 Forbidden 반환")
        void signup_pendingAgency_403() throws Exception {
            given(agenciesService.requestAgencyAccount(any()))
                    .willThrow(new IllegalStateException("아직 등록 대기중인 대행사 입니다. 등록이 된 이후 다시 요청해주세요."));

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 등록 거부된 대행사에 대한 추가 요청 — 403 Forbidden 반환")
        void signup_rejectedAgency_403() throws Exception {
            given(agenciesService.requestAgencyAccount(any()))
                    .willThrow(new IllegalStateException("등록이 거부된 대행사 입니다. 관리자에게 문의해주세요."));

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isForbidden());
        }

        // ── 유효성 검증 실패 케이스 ──

        @Test
        @DisplayName("유효성 실패: 가입자 이름이 공백 — 400 Bad Request")
        void signup_blankName_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", "대행사주소", 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 이메일 형식 오류 — 400 Bad Request")
        void signup_invalidEmail_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "이메일아님", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", "대행사주소", 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 전화번호 패턴 불일치 — 400 Bad Request")
        void signup_invalidPhoneNumber_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "01012345678형식아님",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", "대행사주소", 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 대행사 상호명이 공백 — 400 Bad Request")
        void signup_blankAgencyName_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "", "123-45-67890", "대행사주소", 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 사업자등록번호가 공백 — 400 Bad Request")
        void signup_blankBusinessNumber_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "", "대행사주소", 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 대행사 주소가 255자 초과 — 400 Bad Request")
        void signup_agencyAddressTooLong_400() throws Exception {
            String longAddress = "주".repeat(256);
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", longAddress, 5.20);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 수수료율이 null — 400 Bad Request")
        void signup_nullFeeRate_400() throws Exception {
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", "대행사주소", null);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }

        @Test
        @DisplayName("유효성 실패: 수수료율이 소수점 2자리 초과 — 400 Bad Request")
        void signup_feeRateFractionExceeded_400() throws Exception {
            // @Digits(integer = 5, fraction = 2) 위반: 소수점 3자리
            AgencyCreateRequest req = new AgencyCreateRequest(
                    "홍길동", "test@careflow.com", "password123", "010-1234-5678",
                    "서울특별시 강남구", "상세주소", "테스트대행사", "123-45-67890", "대행사주소", 5.123);

            mockMvc.perform(post("/api/agencies/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertInstanceOf(
                            MethodArgumentNotValidException.class, result.getResolvedException()));
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/agencies/agency
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agencies/agency — 대행사 정보 조회")
    class GetAgency {

        @Test
        @DisplayName("성공: 등록된 대행사 조회 — 200 OK 반환")
        void getAgency_found_200() throws Exception {
            // Agencies 는 외부 의존 없는 mock 객체로 대체 (실 엔티티 생성 불필요)
            Agencies mockAgency = org.mockito.Mockito.mock(Agencies.class);
            given(agenciesService.findByAgencyName("테스트대행사")).willReturn(mockAgency);

            mockMvc.perform(get("/api/agencies/agency")
                            .param("agencyName", "테스트대행사"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 대행사 조회 — 404 Not Found 반환")
        void getAgency_notFound_404() throws Exception {
            given(agenciesService.findByAgencyName("없는대행사"))
                    .willThrow(new NoSuchElementException("해당 대행사 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agencies/agency")
                            .param("agencyName", "없는대행사"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: agencyName 파라미터 누락 — 400 Bad Request 반환")
        void getAgency_missingParam_400() throws Exception {
            mockMvc.perform(get("/api/agencies/agency"))
                    .andExpect(status().isBadRequest());
        }
    }
}
