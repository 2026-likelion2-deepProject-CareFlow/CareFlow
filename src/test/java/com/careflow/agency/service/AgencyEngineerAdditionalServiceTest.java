package com.careflow.agency.service;

import com.careflow.agency.dto.response.EngineerLmsStatusResponse;
import com.careflow.agency.dto.response.EngineerRealtimeStatusResponse;
import com.careflow.agency.dto.response.EngineerRecommendResponse;
import com.careflow.agency.dto.response.EngineerReviewListResponse;
import com.careflow.agency.dto.response.EngineerSettlementResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.lms.entity.LmsConfirmation;
import com.careflow.lms.repository.LmsConfirmationRepository;
import com.careflow.region.repository.RegionRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * AgencyEngineerService 추가 API 단위 테스트
 * - getRecommendedEngineers
 * - getEngineersRealtimeStatus
 * - getEngineerSettlements
 * - getEngineerLmsStatus
 * - getEngineerReviews
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyEngineerService 추가 API 단위 테스트")
class AgencyEngineerAdditionalServiceTest {

    @InjectMocks
    private AgencyEngineerService agencyEngineerService;

    @Mock private UserRepository userRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;
    @Mock private EngineerExpertBrandRepository expertBrandRepository;
    @Mock private EngineerServiceRegionRepository serviceRegionRepository;
    @Mock private EngineerScheduleRepository engineerScheduleRepository;
    @Mock private ApplianceCategoryRepository categoryRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private AsRequestRepository asRequestRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private LmsConfirmationRepository lmsConfirmationRepository;

    private static final Long AGENCY_USER_ID   = 1L;
    private static final Long ENGINEER_USER_ID = 10L;
    private static final Long AGENCY_ID        = 100L;

    // ══════════════════════════════════════════════════════════════
    //  1. 추천 기사 목록 (getRecommendedEngineers)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("추천 기사 목록 조회 - getRecommendedEngineers")
    class GetRecommendedEngineers {

        @Test
        @DisplayName("성공: LMS 이수 완료 + AVAILABLE 근무표 기사 반환")
        void success_returnsFilteredList() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, true);

            AsRequest asRequest = mock(AsRequest.class);
            given(asRequest.getScheduledDate()).willReturn(LocalDate.of(2026, 6, 15));

            EngineerSchedule availableSchedule = mock(EngineerSchedule.class);
            given(availableSchedule.getStatus()).willReturn(ScheduleStatus.AVAILABLE);
            given(availableSchedule.getTimeSlots()).willReturn(List.of());

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(asRequestRepository.findById(1L)).willReturn(Optional.of(asRequest));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(profile));
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    anyLong(), any(), any())).willReturn(List.of(availableSchedule));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            List<EngineerRecommendResponse> result =
                    agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 1L);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(ENGINEER_USER_ID);
            assertThat(result.get(0).getIsLmsCompleted()).isTrue();
        }

        @Test
        @DisplayName("성공: LMS 미이수 기사 필터링 — 빈 리스트 반환")
        void success_lmsNotCompleted_returnsEmpty() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, false); // LMS 미이수

            AsRequest asRequest = mock(AsRequest.class);
            given(asRequest.getScheduledDate()).willReturn(LocalDate.of(2026, 6, 15));

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(asRequestRepository.findById(1L)).willReturn(Optional.of(asRequest));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(profile));

            // When
            List<EngineerRecommendResponse> result =
                    agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 1L);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — NoSuchElementException")
        void fail_requestNotFound_throwsNoSuchElement() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(asRequestRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 A/S 요청이 존재하지 않습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 실시간 배정 현황 (getEngineersRealtimeStatus)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("실시간 배정 현황 조회 - getEngineersRealtimeStatus")
    class GetEngineersRealtimeStatus {

        @Test
        @DisplayName("성공: 배정 없는 기사 — asStatus null 반환")
        void success_noActiveAssignment_returnsNullStatus() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, true);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(profile));
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(asAssignmentRepository.findActiveByEngineerId(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            List<EngineerRealtimeStatusResponse> result =
                    agencyEngineerService.getEngineersRealtimeStatus(AGENCY_USER_ID);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAsStatus()).isNull();
            assertThat(result.get(0).getProduct()).isNull();
        }

        @Test
        @DisplayName("성공: 소속 기사 없으면 빈 리스트 반환")
        void success_noEngineers_returnsEmptyList() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of());

            // When
            List<EngineerRealtimeStatusResponse> result =
                    agencyEngineerService.getEngineersRealtimeStatus(AGENCY_USER_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 기사 정산 목록 (getEngineerSettlements)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("기사 정산 목록 조회 - getEngineerSettlements")
    class GetEngineerSettlements {

        @Test
        @DisplayName("성공: 정산 내역 1건 반환")
        void success_returnsSettlementList() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, true);

            AsRequest asRequest = mock(AsRequest.class);
            given(asRequest.getId()).willReturn(100L);
            given(asRequest.getScheduledDate()).willReturn(LocalDate.of(2026, 6, 15));

            Settlement settlement = mock(Settlement.class);
            given(settlement.getId()).willReturn(1L);
            given(settlement.getAsRequest()).willReturn(asRequest);
            given(settlement.getGrossAmount()).willReturn(150000);
            given(settlement.getPlatformFee()).willReturn(15000);
            given(settlement.getAgencyFee()).willReturn(7500);
            given(settlement.getEngineerNetAmount()).willReturn(127500);
            given(settlement.getStatus()).willReturn("PAID");
            given(settlement.getCreatedAt()).willReturn(null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(settlementRepository.findByEngineerIdWithRequest(ENGINEER_USER_ID))
                    .willReturn(List.of(settlement));

            // When
            List<EngineerSettlementResponse> result =
                    agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSettlementId()).isEqualTo(1L);
            assertThat(result.get(0).getGrossAmount()).isEqualTo(150000);
            assertThat(result.get(0).getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 — IllegalAccessException")
        void fail_otherAgency_throwsIllegalAccess() {
            // Given
            User agencyUser    = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L); // 다른 대행사
            EngineerProfile profile = completedProfile(otherEngineer, true);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(20L)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, 20L))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — NoSuchElementException")
        void fail_engineerNotFound_throwsNoSuchElement() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 기사의 프로필 정보가 존재하지 않습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 기사 LMS 이수 현황 (getEngineerLmsStatus)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("기사 LMS 이수 현황 조회 - getEngineerLmsStatus")
    class GetEngineerLmsStatus {

        @Test
        @DisplayName("성공: LMS 이수 완료 + 이수 이력 1건 반환")
        void success_lmsCompleted_returnsConfirmations() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, true);

            LmsConfirmation confirmation = mock(LmsConfirmation.class);
            com.careflow.lms.entity.LmsContent content = mock(com.careflow.lms.entity.LmsContent.class);
            given(content.getContentId()).willReturn(1L);
            given(content.getTitle()).willReturn("냉장고 수리 기초");
            given(content.getRequiredLevel()).willReturn(com.careflow.lms.entity.LmsContent.RequiredLevel.BEGINNER);
            given(content.getVersion()).willReturn("1.0");
            given(confirmation.getContent()).willReturn(content);
            given(confirmation.getConfirmedAt()).willReturn(null);
            given(confirmation.getConfirmedVersion()).willReturn("1.0");

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(lmsConfirmationRepository.findByUserIdAndYear(anyLong(), anyInt()))
                    .willReturn(List.of(confirmation));

            // When
            EngineerLmsStatusResponse result =
                    agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result.getIsLmsCompleted()).isTrue();
            assertThat(result.getConfirmations()).hasSize(1);
            assertThat(result.getConfirmations().get(0).getTitle()).isEqualTo("냉장고 수리 기초");
        }

        @Test
        @DisplayName("성공: LMS 미이수 — isLmsCompleted false, 이수 이력 빈 배열")
        void success_lmsNotCompleted_emptyConfirmations() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, false); // LMS 미이수

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(lmsConfirmationRepository.findByUserIdAndYear(anyLong(), anyInt()))
                    .willReturn(List.of());

            // When
            EngineerLmsStatusResponse result =
                    agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result.getIsLmsCompleted()).isFalse();
            assertThat(result.getConfirmations()).isEmpty();
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 — IllegalAccessException")
        void fail_otherAgency_throwsIllegalAccess() {
            // Given
            User agencyUser    = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L);
            EngineerProfile profile = completedProfile(otherEngineer, true);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(20L)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, 20L))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  5. 기사 수신 리뷰 목록 (getEngineerReviews)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("기사 수신 리뷰 목록 조회 - getEngineerReviews")
    class GetEngineerReviews {

        @Test
        @DisplayName("성공: 공개 리뷰 2건 반환")
        void success_returnsVisibleReviews() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            User customer   = mock(User.class);
            given(customer.getName()).willReturn("김고객");

            EngineerProfile profile = completedProfile(engineer, true);

            Review review1 = mock(Review.class);
            given(review1.getId()).willReturn(1L);
            given(review1.getCustomer()).willReturn(customer);
            given(review1.getRating()).willReturn(5);
            given(review1.getContent()).willReturn("친절했어요");
            given(review1.getCreatedAt()).willReturn(null);

            Review review2 = mock(Review.class);
            given(review2.getId()).willReturn(2L);
            given(review2.getCustomer()).willReturn(customer);
            given(review2.getRating()).willReturn(4);
            given(review2.getContent()).willReturn("빠른 수리");
            given(review2.getCreatedAt()).willReturn(null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(reviewRepository.findVisibleByEngineerId(ENGINEER_USER_ID))
                    .willReturn(List.of(review1, review2));

            // When
            EngineerReviewListResponse result =
                    agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result.getTotalReviews()).isEqualTo(2);
            assertThat(result.getAvgRating()).isEqualTo(4.5);
            assertThat(result.getReviews()).hasSize(2);
            assertThat(result.getReviews().get(0).getCustomerName()).isEqualTo("김고객");
        }

        @Test
        @DisplayName("성공: 리뷰 없으면 빈 리스트 반환")
        void success_noReviews_returnsEmpty() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, true);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(reviewRepository.findVisibleByEngineerId(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            EngineerReviewListResponse result =
                    agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result.getTotalReviews()).isZero();
            assertThat(result.getReviews()).isEmpty();
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 — IllegalAccessException")
        void fail_otherAgency_throwsIllegalAccess() {
            // Given
            User agencyUser    = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L);
            EngineerProfile profile = completedProfile(otherEngineer, true);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(20L)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, 20L))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  픽스처 헬퍼
    // ═══════════════════════════════════════════════════════════

    private User agencyUser(Long userId, Long agencyId) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(agencyId);
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getAgency()).willReturn(agency);
        given(user.getRole()).willReturn(Role.AGENCY);
        return user;
    }

    private User engineerUser(Long userId, Long agencyId) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(agencyId);
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("기사" + userId);
        given(user.getAgency()).willReturn(agency);
        given(user.getRole()).willReturn(Role.ENGINEER);
        return user;
    }

    /** lmsCompleted 값을 지정할 수 있는 프로필 생성 */
    private EngineerProfile completedProfile(User engineer, boolean lmsCompleted) {
        ApplianceCategory category = mock(ApplianceCategory.class);
        given(category.getCategoryId()).willReturn(5);
        given(category.getDepth()).willReturn(2);
        given(category.getName()).willReturn("냉장고");

        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "소개글");
        if (lmsCompleted) {
            profile.completeLms();
        }
        return profile;
    }
}
