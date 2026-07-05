package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyEngineerService 단위 테스트")
class AgencyEngineerServiceTest {

    @InjectMocks
    private AgencyEngineerService agencyEngineerService;

    @Mock private UserRepository userRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;
    @Mock private EngineerExpertBrandRepository expertBrandRepository;
    @Mock private EngineerServiceRegionRepository serviceRegionRepository;
    @Mock private EngineerScheduleRepository engineerScheduleRepository;
    @Mock private ApplianceCategoryRepository categoryRepository;
    @Mock private RegionRepository regionRepository;
    // 추가 API 구현으로 신규 추가된 의존성 (기존 테스트와 독립적으로 동작)
    @Mock private com.careflow.assignment.repository.AsAssignmentRepository asAssignmentRepository;
    @Mock private com.careflow.as_request.repository.AsRequestRepository asRequestRepository;
    @Mock private com.careflow.settlement.repository.SettlementRepository settlementRepository;
    @Mock private com.careflow.review.repository.ReviewRepository reviewRepository;
    @Mock private com.careflow.lms.repository.LmsConfirmationRepository lmsConfirmationRepository;

    // ─── 공통 ID 상수 ───────────────────────────────────────────────────
    private static final Long AGENCY_USER_ID   = 1L;
    private static final Long ENGINEER_USER_ID = 10L;
    private static final Long AGENCY_ID        = 100L;

    // ══════════════════════════════════════════════════════════════
    //  1. 소속 기사 목록 조회 (getAgencyEngineers)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 목록 조회 - getAgencyEngineers")
    class GetAgencyEngineers {

        @Test
        @DisplayName("성공: 소속 기사 2명 반환 — 각 필드 정상 매핑")
        void success_returnsEngineerList() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer1  = engineerUser(10L, AGENCY_ID);
            User engineer2  = engineerUser(11L, AGENCY_ID);

            EngineerProfile profile1 = completedProfile(engineer1, 5);
            EngineerProfile profile2 = completedProfile(engineer2, 3);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID))
                    .willReturn(List.of(profile1, profile2));
            given(serviceRegionRepository.findByEngineer_Id(anyLong())).willReturn(List.of());
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    anyLong(), any(LocalDate.class), any(LocalDate.class)))
                    .willReturn(List.of());

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEngineerUserId()).isEqualTo(10L);
            assertThat(result.get(1).getEngineerUserId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("성공: 소속 기사 없음 — 빈 리스트 반환 (예외 아님)")
        void success_emptyList_whenNoEngineers() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of());

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 오늘 근무표 존재 시 실제 근무 상태(BOOKED)를 반환한다")
        void success_returnsActualScheduleStatus() {
            // Given
            User agencyUser  = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer    = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile  profile  = completedProfile(engineer, 5);
            EngineerSchedule schedule = mockSchedule(ScheduleStatus.BOOKED);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(profile));
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    eq(ENGINEER_USER_ID), any(), any()))
                    .willReturn(List.of(schedule));

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID);

            // Then
            assertThat(result.get(0).getCurrentWorkStatus()).isEqualTo("BOOKED");
        }

        @Test
        @DisplayName("성공: 오늘 근무표 없으면 OFF 반환")
        void success_returnsOff_whenNoScheduleToday() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(profile));
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    anyLong(), any(), any()))
                    .willReturn(List.of()); // 근무표 없음

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID);

            // Then
            assertThat(result.get(0).getCurrentWorkStatus()).isEqualTo("OFF");
        }

        @Test
        @DisplayName("실패: 로그인 유저에 대행사 정보 없음 — NoSuchElementException")
        void fail_noAgency_throwsException() {
            // Given — agency 가 null 인 일반 유저
            User noAgencyUser = mock(User.class);
            given(noAgencyUser.getAgency()).willReturn(null);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(noAgencyUser));

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("소속 대행사 정보가 없습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 소속 기사 상세 조회 (getAgencyEngineerDetail)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 상세 조회 - getAgencyEngineerDetail")
    class GetAgencyEngineerDetail {

        @Test
        @DisplayName("성공: 소속 기사 상세 반환 — 브랜드·지역 포함")
        void success_returnsDetail() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            EngineerExpertBrand brand = EngineerExpertBrand.builder()
                    .engineer(engineer).brandName("삼성").build();
            Regions region = district(101);
            EngineerServiceRegion serviceRegion = EngineerServiceRegion.builder()
                    .engineer(engineer).region(region).build();

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID))
                    .willReturn(List.of(brand));
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID))
                    .willReturn(List.of(serviceRegion));

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, ENGINEER_USER_ID);

            // Then
            assertThat(result.getEngineerUserId()).isEqualTo(ENGINEER_USER_ID);
            assertThat(result.getExpertBrands()).containsExactly("삼성");
            assertThat(result.getServiceRegionIds()).containsExactly(101);
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 시도 — IllegalAccessException")
        void fail_otherAgencyEngineer_throwsIllegalAccess() {
            // Given
            User agencyUser   = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L); // 다른 대행사 소속
            EngineerProfile profile = completedProfile(otherEngineer, 5);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(20L)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, 20L))
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
                    agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 기사의 프로필 정보가 존재하지 않습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 소속 기사 프로필 수정 (updateAgencyEngineerProfile)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 프로필 수정 - updateAgencyEngineerProfile")
    class UpdateAgencyEngineerProfile {

        @Test
        @DisplayName("성공: 카테고리·브랜드·지역 모두 수정")
        void success_updateAll() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);
            ApplianceCategory newCategory = mockCategory(7, 2);
            Regions newRegion = district(201);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(7, List.of("다이슨"), List.of(201));

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(categoryRepository.findById(7)).willReturn(Optional.of(newCategory));
            given(regionRepository.findById(201)).willReturn(Optional.of(newRegion));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.updateAgencyEngineerProfile(
                            AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            assertThat(result.getCategoryId()).isEqualTo(7);
            assertThat(result.getExpertBrands()).containsExactly("다이슨");
            assertThat(result.getServiceRegionIds()).containsExactly(201);
        }

        @Test
        @DisplayName("성공: categoryId 만 null — 카테고리 변경 없이 나머지만 수정")
        void success_updateBrandsAndRegionOnly() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5); // 기존 category mock(5, depth=2)
            Regions newRegion = district(202);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("LG"), List.of(202));

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(regionRepository.findById(202)).willReturn(Optional.of(newRegion));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.updateAgencyEngineerProfile(
                            AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then — 카테고리는 기존값(5) 유지, 브랜드·지역만 반영
            assertThat(result.getCategoryId()).isEqualTo(5);
            assertThat(result.getExpertBrands()).containsExactly("LG");
            assertThat(result.getServiceRegionIds()).containsExactly(202);
        }

        @Test
        @DisplayName("실패: categoryId 가 depth=1(대분류) — IllegalArgumentException")
        void fail_categoryDepth1_throwsException() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);
            ApplianceCategory rootCategory = mockCategory(1, 1); // depth=1 대분류

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(1, null, null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(categoryRepository.findById(1)).willReturn(Optional.of(rootCategory));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.updateAgencyEngineerProfile(
                            AGENCY_USER_ID, ENGINEER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
        }

        @Test
        @DisplayName("실패: serviceRegionId 가 depth=1(시 단위) — IllegalArgumentException")
        void fail_regionDepth1_throwsException() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);
            Regions cityLevel = Regions.builder().name("서울시").depth(1).sortOrder(0).build();
            ReflectionTestUtils.setField(cityLevel, "id", 99);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, List.of(99));

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID))
                    .willReturn(Optional.of(profile));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(regionRepository.findById(99)).willReturn(Optional.of(cityLevel));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.updateAgencyEngineerProfile(
                            AGENCY_USER_ID, ENGINEER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 수정 시도 — IllegalAccessException")
        void fail_otherAgencyEngineer_throwsIllegalAccess() {
            // Given
            User agencyUser    = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L);
            EngineerProfile profile = completedProfile(otherEngineer, 5);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("삼성"), null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(20L)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, 20L, request))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }

        @Test
        @DisplayName("성공: 연락처·이메일 수정 — 중복 아닌 새 이메일이면 updateContact 호출")
        void success_updateContact() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID); // 기존 email: engineer10@test.com
            EngineerProfile profile = completedProfile(engineer, 5);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, null, "010-9999-9999", "new@test.com", null, null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            verify(engineer).updateContact("010-9999-9999", "new@test.com");
        }

        @Test
        @DisplayName("실패: 이미 사용 중인 이메일로 변경 시도 — IllegalArgumentException, updateContact 호출 안 됨")
        void fail_duplicateEmail_throwsException() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID); // 기존 email: engineer10@test.com
            EngineerProfile profile = completedProfile(engineer, 5);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, null, null, "dup@test.com", null, null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이미 사용 중인 이메일입니다.");
            verify(engineer, never()).updateContact(any(), any());
        }

        @Test
        @DisplayName("성공: 경력 시작 연도 변경 시 기술 등급이 함께 자동 재산정됨")
        void success_updateCareerYear_recalculatesSkillLevel() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5); // 초기 skillLevel = INTERMEDIATE

            // 16년차 경력 → ADVANCED로 재산정되어야 함
            int longAgo = LocalDate.now().getYear() - 16;
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, null, null, null, longAgo, null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            assertThat(profile.getCareerStartedYear()).isEqualTo(longAgo);
            assertThat(profile.getSkillLevel()).isEqualTo(SkillLevel.ADVANCED);
        }

        @Test
        @DisplayName("실패: 경력 시작 연도가 미래 — IllegalArgumentException")
        void fail_futureCareerYear_throwsException() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            int futureYear = LocalDate.now().getYear() + 1;
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, null, null, null, futureYear, null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("경력 시작 연도는 미래일 수 없습니다.");
        }

        @Test
        @DisplayName("성공: 소개(introduction)만 수정 — 경력·등급은 변경되지 않음")
        void success_updateIntroductionOnly() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5); // careerStartedYear=2020, skillLevel=INTERMEDIATE

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, null, null, null, null, "새로운 소개글");

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));
            given(expertBrandRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());
            given(serviceRegionRepository.findByEngineer_Id(ENGINEER_USER_ID)).willReturn(List.of());

            // When
            agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            assertThat(profile.getIntroduction()).isEqualTo("새로운 소개글");
            assertThat(profile.getCareerStartedYear()).isEqualTo(2020);
            assertThat(profile.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE);
        }

        @Test
        @DisplayName("성공: expertBrands 빈 배열 전달 — 전체 삭제로 처리됨(기존 !isEmpty() 가드 제거 확인)")
        void success_emptyExpertBrands_clearsAll() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of(), null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            assertThat(result.getExpertBrands()).isEmpty();
            InOrder inOrder = inOrder(expertBrandRepository);
            inOrder.verify(expertBrandRepository).deleteByEngineer_Id(ENGINEER_USER_ID);
            inOrder.verify(expertBrandRepository).flush();
            inOrder.verify(expertBrandRepository).saveAll(List.of());
        }

        @Test
        @DisplayName("성공: serviceRegionIds 빈 배열 전달 — 전체 삭제로 처리됨(기존 !isEmpty() 가드 제거 확인)")
        void success_emptyServiceRegionIds_clearsAll() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, List.of());

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then
            assertThat(result.getServiceRegionIds()).isEmpty();
            InOrder inOrder = inOrder(serviceRegionRepository);
            inOrder.verify(serviceRegionRepository).deleteByEngineer_Id(ENGINEER_USER_ID);
            inOrder.verify(serviceRegionRepository).flush();
            inOrder.verify(serviceRegionRepository).saveAll(List.of());
        }

        @Test
        @DisplayName("성공: 브랜드 재삽입 순서가 delete → flush → saveAll 인지 확인 (Duplicate entry 회귀 방지)")
        void success_saveExpertBrands_flushesBeforeReinsert() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerProfile profile = completedProfile(engineer, 5);

            // 기존 값과 겹치는 브랜드를 포함한 새 목록 — flush 없이 재삽입하면 uk_eng_brand 위반 발생 가능
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("삼성", "LG"), null);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(engineerProfileRepository.findByUser_Id(ENGINEER_USER_ID)).willReturn(Optional.of(profile));

            // When
            agencyEngineerService.updateAgencyEngineerProfile(AGENCY_USER_ID, ENGINEER_USER_ID, request);

            // Then — delete가 반드시 saveAll(insert)보다 먼저, 그리고 그 사이에 flush가 호출되어야 함
            InOrder inOrder = inOrder(expertBrandRepository);
            inOrder.verify(expertBrandRepository).deleteByEngineer_Id(ENGINEER_USER_ID);
            inOrder.verify(expertBrandRepository).flush();
            inOrder.verify(expertBrandRepository).saveAll(anyList());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 소속 기사 월간 근무표 조회 (getAgencyEngineerSchedules)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 월간 근무표 조회 - getAgencyEngineerSchedules")
    class GetAgencyEngineerSchedules {

        @Test
        @DisplayName("성공: 해당 월 근무표 2건 반환")
        void success_returnsScheduleList() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);
            EngineerSchedule s1 = mockSchedule(ScheduleStatus.AVAILABLE);
            EngineerSchedule s2 = mockSchedule(ScheduleStatus.BOOKED);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(userRepository.findById(ENGINEER_USER_ID)).willReturn(Optional.of(engineer));
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    eq(ENGINEER_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .willReturn(List.of(s1, s2));

            // When
            List<ScheduleResponse> result =
                    agencyEngineerService.getAgencyEngineerSchedules(
                            AGENCY_USER_ID, ENGINEER_USER_ID, 2026, 6);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공: 근무표 없으면 빈 리스트 반환")
        void success_emptyList_whenNoSchedule() throws Exception {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User engineer   = engineerUser(ENGINEER_USER_ID, AGENCY_ID);

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(userRepository.findById(ENGINEER_USER_ID)).willReturn(Optional.of(engineer));
            given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                    anyLong(), any(), any()))
                    .willReturn(List.of());

            // When
            List<ScheduleResponse> result =
                    agencyEngineerService.getAgencyEngineerSchedules(
                            AGENCY_USER_ID, ENGINEER_USER_ID, 2026, 6);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 근무표 조회 시도 — IllegalAccessException")
        void fail_otherAgencyEngineer_throwsIllegalAccess() {
            // Given
            User agencyUser    = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            User otherEngineer = engineerUser(20L, 999L); // 다른 대행사

            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(userRepository.findById(20L)).willReturn(Optional.of(otherEngineer));

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getAgencyEngineerSchedules(
                            AGENCY_USER_ID, 20L, 2026, 6))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — NoSuchElementException")
        void fail_engineerNotFound_throwsNoSuchElement() {
            // Given
            User agencyUser = agencyUser(AGENCY_USER_ID, AGENCY_ID);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    agencyEngineerService.getAgencyEngineerSchedules(
                            AGENCY_USER_ID, 999L, 2026, 6))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 기사 정보가 존재하지 않습니다.");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  픽스처 헬퍼
    // ═══════════════════════════════════════════════════════════

    /** 대행사 관리자 유저 생성 */
    private User agencyUser(Long userId, Long agencyId) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(agencyId);

        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getAgency()).willReturn(agency);
        given(user.getRole()).willReturn(Role.AGENCY);
        return user;
    }

    /** 특정 대행사 소속 엔지니어 유저 생성 */
    private User engineerUser(Long userId, Long agencyId) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(agencyId);

        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("기사" + userId);
        given(user.getEmail()).willReturn("engineer" + userId + "@test.com");
        given(user.getPhone()).willReturn("010-0000-0000");
        given(user.getAgency()).willReturn(agency);
        given(user.getRole()).willReturn(Role.ENGINEER);
        return user;
    }

    /** 프로필 완성 상태 EngineerProfile 생성 */
    private EngineerProfile completedProfile(User engineer, int categoryId) {
        ApplianceCategory category = mockCategory(categoryId, 2);
        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "소개글");
        return profile;
    }

    /** ApplianceCategory mock */
    private ApplianceCategory mockCategory(int categoryId, int depth) {
        ApplianceCategory category = mock(ApplianceCategory.class);
        given(category.getCategoryId()).willReturn(categoryId);
        given(category.getDepth()).willReturn(depth);
        given(category.getName()).willReturn("카테고리" + categoryId);
        return category;
    }

    /** 구 단위(depth=2) Regions 생성 */
    private Regions district(int regionId) {
        Regions region = Regions.builder().name("강남구").depth(2).sortOrder(0).build();
        ReflectionTestUtils.setField(region, "id", regionId);
        return region;
    }

    /** EngineerSchedule mock */
    private EngineerSchedule mockSchedule(ScheduleStatus status) {
        EngineerSchedule schedule = mock(EngineerSchedule.class);
        given(schedule.getScheduleId()).willReturn(1L);
        given(schedule.getWorkDate()).willReturn(LocalDate.of(2026, 6, 3));
        given(schedule.getStatus()).willReturn(status);
        given(schedule.getTimeSlots()).willReturn(List.of());
        return schedule;
    }

    /** AgencyEngineerProfileUpdateRequest 리플렉션 생성 */
    private AgencyEngineerProfileUpdateRequest buildUpdateRequest(
            Integer categoryId, List<String> brands, List<Integer> regionIds) {
        return buildUpdateRequest(categoryId, brands, regionIds, null, null, null, null);
    }

    private AgencyEngineerProfileUpdateRequest buildUpdateRequest(
            Integer categoryId, List<String> brands, List<Integer> regionIds,
            String phone, String email, Integer careerStartedYear, String introduction) {
        try {
            Constructor<AgencyEngineerProfileUpdateRequest> constructor =
                    AgencyEngineerProfileUpdateRequest.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AgencyEngineerProfileUpdateRequest req = constructor.newInstance();
            ReflectionTestUtils.setField(req, "categoryId", categoryId);
            ReflectionTestUtils.setField(req, "expertBrands", brands);
            ReflectionTestUtils.setField(req, "serviceRegionIds", regionIds);
            ReflectionTestUtils.setField(req, "phone", phone);
            ReflectionTestUtils.setField(req, "email", email);
            ReflectionTestUtils.setField(req, "careerStartedYear", careerStartedYear);
            ReflectionTestUtils.setField(req, "introduction", introduction);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
