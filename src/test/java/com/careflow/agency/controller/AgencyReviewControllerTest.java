package com.careflow.agency.controller;

import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.agency.service.AgencyReviewService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencyReviewController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyReviewController 테스트")
class AgencyReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AgencyReviewService agencyReviewService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private static final Long AGENCY_USER_ID = 1L;
    private static final Long AGENCY_ID = 100L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY", AGENCY_ID);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private AgencyReviewListResponse sampleResponse() {
        AgencyReviewListResponse.Stats stats =
                new AgencyReviewListResponse.Stats(4.8, 2148L, 78.6, 256L, 0.2, 156L, 32L);
        AgencyReviewListResponse.ReviewSummary summary = new AgencyReviewListResponse.ReviewSummary(
                1L, 1L, "김민수", 123L, "김현수", "퀵케어 서비스",
                "삼성", "AF17B7538WZ", "2024-06-18", "13:00",
                5, "리뷰 내용", true, LocalDateTime.of(2024, 6, 18, 15, 30));
        return new AgencyReviewListResponse(
                stats, List.of(summary), 2148L, 27, 0, 10);
    }

    @Nested
    @DisplayName("GET /api/agency/reviews — 리뷰 목록 조회")
    class GetReviews {

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK, 응답 구조 검증")
        void success_200_returnsReviewList() throws Exception {
            given(agencyReviewService.getReviews(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.avgRating").value(4.8))
                    .andExpect(jsonPath("$.stats.totalCount").value(2148))
                    .andExpect(jsonPath("$.stats.fiveStarRate").value(78.6))
                    .andExpect(jsonPath("$.stats.newThisMonth").value(256))
                    .andExpect(jsonPath("$.content[0].reviewId").value(1))
                    .andExpect(jsonPath("$.content[0].customerName").value("김민수"))
                    .andExpect(jsonPath("$.content[0].rating").value(5))
                    .andExpect(jsonPath("$.totalElements").value(2148))
                    .andExpect(jsonPath("$.totalPages").value(27))
                    .andExpect(jsonPath("$.currentPage").value(0));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/reviews").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: page/size 미전달 시 기본값(page=0, size=10)으로 Service 호출")
        void success_defaultPageable() throws Exception {
            given(agencyReviewService.getReviews(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/reviews"))
                    .andExpect(status().isOk());

            verify(agencyReviewService).getReviews(any(), any(), eq(PageRequest.of(0, 10)));
        }

        @Test
        @DisplayName("TC-C-4: 요청 바디 없이 호출해도 정상 동작")
        void success_noRequestBody() throws Exception {
            given(agencyReviewService.getReviews(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/reviews?page=0&size=10"))
                    .andExpect(status().isOk());
        }
    }
}
