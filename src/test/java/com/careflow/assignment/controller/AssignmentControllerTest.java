package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AssignmentDetailResponse;
import com.careflow.assignment.dto.AssignmentFilterResponse;
import com.careflow.assignment.dto.AssignmentReassignResponse;
import com.careflow.assignment.dto.AssignmentResponse;
import com.careflow.assignment.service.AgenciesAssignmentService;
import com.careflow.assignment.service.AssignmentDetailService;
import com.careflow.assignment.service.AssignmentFilterService;
import com.careflow.assignment.service.AssignmentReassignService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.common.enums.AssignType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import java.util.NoSuchElementException;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignmentController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AssignmentController 테스트")
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgenciesAssignmentService agenciesAssignmentService;
    @MockitoBean
    private AssignmentDetailService assignmentDetailService;
    @MockitoBean
    private AssignmentFilterService assignmentFilterService;
    @MockitoBean
    private AssignmentReassignService assignmentReassignService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    // AGENCY 권한 테스트용 사용자
    private CustomUserDetails agencyUser() {
        return new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY");
    }

    // CUSTOMER 권한 테스트용 사용자 (권한 없음)
    private CustomUserDetails customerUser() {
        return new CustomUserDetails(2L, "customer@test.com", "pw", "CUSTOMER");
    }

    private AssignmentResponse sampleResponse() {
        return new AssignmentResponse(
                1L, 10L, 5L, "홍길동",
                3L, AssignType.MANUAL, "WAITING",
                LocalDateTime.of(2026, 6, 25, 10, 0),
                null, null, null
        );
    }

    @Nested
    @DisplayName("GET /api/agencies/assignment — 대행사 배차 내역 조회")
    class GetAgenciesAssignment {

        @Test
        @DisplayName("성공: 배차 내역 존재 — 200 OK 및 리스트 반환")
        void getAssignment_withData_200() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].assignmentId").value(1L))
                    .andExpect(jsonPath("$[0].engineerName").value("홍길동"))
                    .andExpect(jsonPath("$[0].status").value("WAITING"));
        }

        @Test
        @DisplayName("성공: 배차 내역 없음 — 204 No Content 반환")
        void getAssignment_noData_204() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없는 사용자 — 401 Unauthorized 반환")
        void getAssignment_noAuthority_401() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(get("/api/assignment")
                            .with(user(customerUser())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 비인증 요청 — 401 Unauthorized 반환")
        void getAssignment_unauthenticated_401() throws Exception {
            mockMvc.perform(get("/api/assignment"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 소속 대행사 없는 관리자 — 403 Forbidden 반환")
        void getAssignment_noAgency_403() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willThrow(new IllegalStateException("소속 대행사 정보가 없습니다."));

            mockMvc.perform(get("/api/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isForbidden());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/assignment/detail/{assignmentId}
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/assignment/detail/{assignmentId} — 배차 상세 조회")
    class GetAssignmentDetail {

        private AssignmentDetailResponse sampleDetail() {
            return new AssignmentDetailResponse(
                    1L, "WAITING", LocalDateTime.of(2026, 6, 25, 10, 0),
                    10L, LocalDate.of(2026, 7, 1), "10:00", "냉방 불량 발생", "강남구 테헤란로 1",
                    "냉방 불량",
                    "홍길동", "010-1111-2222",
                    "에어컨 Q9000", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1),
                    150000,
                    List.of(),
                    List.of(),
                    new AssignmentDetailResponse.EngineerProfileInfo(
                            1L, "INTERMEDIATE", BigDecimal.valueOf(4.5), 10, "성실한 기사입니다")
            );
        }

        @Test
        @DisplayName("성공: 유효한 assignmentId — 200 OK 및 상세 데이터 반환")
        void getDetail_success_200() throws Exception {
            given(assignmentDetailService.getDetail(any(), any())).willReturn(sampleDetail());

            mockMvc.perform(get("/api/assignment/detail/1")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignmentId").value(1L))
                    .andExpect(jsonPath("$.symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$.customerName").value("홍길동"))
                    .andExpect(jsonPath("$.modelName").value("에어컨 Q9000"))
                    .andExpect(jsonPath("$.avgRepairCost").value(150000))
                    .andExpect(jsonPath("$.engineerProfile.skillLevel").value("INTERMEDIATE"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId — 404 Not Found 반환")
        void getDetail_notFound_404() throws Exception {
            given(assignmentDetailService.getDetail(any(), any()))
                    .willThrow(new NoSuchElementException("해당 배차 내역을 찾을 수 없습니다."));

            mockMvc.perform(get("/api/assignment/detail/999")
                            .with(user(agencyUser())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없음 — 401 Unauthorized 반환")
        void getDetail_noAuthority_401() throws Exception {
            given(assignmentDetailService.getDetail(any(), any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(get("/api/assignment/detail/1")
                            .with(user(customerUser())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized 반환")
        void getDetail_unauthenticated_401() throws Exception {
            mockMvc.perform(get("/api/assignment/detail/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/assignment/search
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/assignment/search — 날짜·상태 필터 배차 조회")
    class SearchAssignments {

        private AssignmentFilterResponse sampleFilterResponse() {
            return new AssignmentFilterResponse(
                    1L, "WAITING", AssignType.MANUAL,
                    LocalDateTime.of(2026, 6, 25, 10, 0), null,
                    10L, LocalDate.of(2026, 7, 1), "10:00", "강남구 테헤란로 1",
                    "냉방 불량",
                    5L, "홍길동"
            );
        }

        @Test
        @DisplayName("성공: 날짜+상태 필터 모두 입력 → 200 OK 및 결과 반환")
        void search_withBothFilters_200() throws Exception {
            given(assignmentFilterService.search(any(), any(), any()))
                    .willReturn(List.of(sampleFilterResponse()));

            mockMvc.perform(get("/api/assignment/search")
                            .param("date", "2026-07-01")
                            .param("status", "WAITING")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].assignmentId").value(1L))
                    .andExpect(jsonPath("$[0].scheduledDate").value("2026-07-01"))
                    .andExpect(jsonPath("$[0].assignmentStatus").value("WAITING"))
                    .andExpect(jsonPath("$[0].symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$[0].engineerName").value("홍길동"));
        }

        @Test
        @DisplayName("성공: 파라미터 없음 → date 기본값 오늘, status 전체 → 200 OK")
        void search_noParams_usesTodayDefault_200() throws Exception {
            given(assignmentFilterService.search(any(), any(), any()))
                    .willReturn(List.of(sampleFilterResponse()));

            mockMvc.perform(get("/api/assignment/search")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("성공: 조건 맞는 배차 없음 → 204 No Content")
        void search_noResult_204() throws Exception {
            given(assignmentFilterService.search(any(), any(), any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/assignment/search")
                            .param("date", "2026-07-01")
                            .with(user(agencyUser())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 유효하지 않은 status 값 → 400 Bad Request")
        void search_invalidStatus_400() throws Exception {
            given(assignmentFilterService.search(any(), any(), any()))
                    .willThrow(new IllegalArgumentException("유효하지 않은 배차 상태입니다."));

            mockMvc.perform(get("/api/assignment/search")
                            .param("status", "INVALID_STATUS")
                            .with(user(agencyUser())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없음 → 401 Unauthorized")
        void search_noAuthority_401() throws Exception {
            given(assignmentFilterService.search(any(), any(), any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(get("/api/assignment/search")
                            .with(user(customerUser())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void search_noToken_401() throws Exception {
            mockMvc.perform(get("/api/assignment/search"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  POST /api/assignment/{assignmentId}/reassign
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/assignment/{assignmentId}/reassign — 재배차")
    class ReassignAssignment {

        @Test
        @DisplayName("성공: AUTO 배차 건 → 새 배차 생성, REASSIGNED 반환")
        void reassign_auto_200() throws Exception {
            given(assignmentReassignService.reassign(any(), any()))
                    .willReturn(AssignmentReassignResponse.reassigned(99L,
                            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족"));

            mockMvc.perform(post("/api/assignment/1/reassign")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("REASSIGNED"))
                    .andExpect(jsonPath("$.newAssignmentId").value(99L))
                    .andExpect(jsonPath("$.matchReason").value(
                            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족"))
                    .andExpect(jsonPath("$.notificationId").isEmpty())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("성공: MANUAL 배차 건 → 재배차 없음, 고객 알림 발송, MANUAL_NOTIFIED 반환")
        void reassign_manual_notified_200() throws Exception {
            given(assignmentReassignService.reassign(any(), any()))
                    .willReturn(AssignmentReassignResponse.manualNotified(55L));

            mockMvc.perform(post("/api/assignment/1/reassign")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("MANUAL_NOTIFIED"))
                    .andExpect(jsonPath("$.notificationId").value(55L))
                    .andExpect(jsonPath("$.newAssignmentId").isEmpty());
        }

        @Test
        @DisplayName("실패: assignmentId 미존재 → 404 Not Found")
        void reassign_notFound_404() throws Exception {
            given(assignmentReassignService.reassign(any(), any()))
                    .willThrow(new NoSuchElementException("해당 배차 내역을 찾을 수 없습니다."));

            mockMvc.perform(post("/api/assignment/999/reassign")
                            .with(user(agencyUser())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: AUTO 재배차 가용 기사 없음 → 403 Forbidden")
        void reassign_noAvailableEngineer_403() throws Exception {
            given(assignmentReassignService.reassign(any(), any()))
                    .willThrow(new IllegalStateException("재배차 가능한 수리 기사가 없습니다."));

            mockMvc.perform(post("/api/assignment/1/reassign")
                            .with(user(agencyUser())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없음 → 401 Unauthorized")
        void reassign_noAuthority_401() throws Exception {
            given(assignmentReassignService.reassign(any(), any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(post("/api/assignment/1/reassign")
                            .with(user(customerUser())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void reassign_noToken_401() throws Exception {
            mockMvc.perform(post("/api/assignment/1/reassign"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
