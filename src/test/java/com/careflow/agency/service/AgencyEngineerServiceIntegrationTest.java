package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgencyEngineerService 통합 테스트 (H2 DB 연동)
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - @Sql cleanup.sql: 각 테스트 전 데이터 전체 초기화 (FK 제약 일시 해제)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyEngineerService 통합 테스트 (H2 DB 연동)")
class AgencyEngineerServiceIntegrationTest {

    @Autowired private AgencyEngineerService agencyEngineerService;

    // 데이터 직접 삽입/조회를 위한 레포지토리
    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private EngineerExpertBrandRepository expertBrandRepository;
    @Autowired private EngineerServiceRegionRepository serviceRegionRepository;
    @Autowired private EngineerScheduleRepository engineerScheduleRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;

    // 공통 픽스처 (각 테스트 전 @BeforeEach 에서 H2에 실제 INSERT)
    private Agencies agency;
    private User agencyUser;
    private User engineerUser;
    private ApplianceCategory rootCategory;
    private ApplianceCategory leafCategory;
    private Regions district;

    @BeforeEach
    void setUp() {
        // 1. 대행사 INSERT
        agency = agencyRepository.save(Agencies.create(
                "테스트대행사", "123-45-67890", "서울시 강남구", 5.0));

        // 2. 대행사 관리자 유저 INSERT
        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("hashed")
                .name("대행사담당자").phone("010-9999-9999")
                .role(Role.AGENCY).agency(agency).build());

        // 3. 기사 유저 INSERT (동일 대행사 소속)
        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("테스트기사").phone("010-1234-5678")
                .role(Role.ENGINEER).agency(agency).build());

        // 4. 카테고리 INSERT (depth=1 → depth=2 순서로 생성)
        rootCategory = categoryRepository.save(ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(ApplianceCategory.createChild("냉장고", rootCategory, 1));

        // 5. 지역 INSERT (구 단위 depth=2)
        district = regionRepository.save(Regions.create("강남구", null, 2, 0));
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 소속 기사 목록 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 목록 조회 - getAgencyEngineers")
    class GetAgencyEngineers {

        @Test
        @DisplayName("성공: H2에 INSERT한 소속 기사 1명 목록 조회 — DB에서 실제로 읽어온 값 검증")
        void success_returnsEngineerFromDb() {
            // Given — 기사 프로필 H2에 INSERT
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.INTERMEDIATE, "소개글");
            engineerProfileRepository.save(profile);

            serviceRegionRepository.save(EngineerServiceRegion.builder()
                    .engineer(engineerUser).region(district).build());

            // When — 실제 DB 조회
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(agencyUser.getId());

            // Then — DB에서 읽어온 값 검증
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEngineerUserId()).isEqualTo(engineerUser.getId());
            assertThat(result.get(0).getName()).isEqualTo("테스트기사");
            assertThat(result.get(0).getCategoryId()).isEqualTo(leafCategory.getCategoryId());
            assertThat(result.get(0).getSkillLevel()).isEqualTo("INTERMEDIATE");
            assertThat(result.get(0).getServiceRegionIds()).contains(district.getId());
        }

        @Test
        @DisplayName("성공: 소속 기사 없으면 빈 리스트 반환 — DB SELECT 결과 0건")
        void success_emptyList_whenNoEngineerInDb() {
            // Given — 기사 프로필 저장 안 함

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(agencyUser.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 오늘 근무표 BOOKED 상태로 INSERT 시 currentWorkStatus=BOOKED 반환")
        void success_currentWorkStatus_BOOKED() {
            // Given
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);

            // 오늘 날짜로 BOOKED 상태 근무표 INSERT
            EngineerSchedule schedule = EngineerSchedule.builder()
                    .user(engineerUser)
                    .workDate(LocalDate.now())
                    .status(ScheduleStatus.BOOKED)
                    .build();
            engineerScheduleRepository.save(schedule);

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(agencyUser.getId());

            // Then — DB에서 조회한 근무 상태가 BOOKED
            assertThat(result.get(0).getCurrentWorkStatus()).isEqualTo("BOOKED");
        }

        @Test
        @DisplayName("성공: 오늘 근무표 없으면 currentWorkStatus=OFF 반환")
        void success_currentWorkStatus_OFF_whenNoSchedule() {
            // Given — 근무표 없이 프로필만 INSERT
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);

            // When
            List<AgencyEngineerSummaryResponse> result =
                    agencyEngineerService.getAgencyEngineers(agencyUser.getId());

            // Then
            assertThat(result.get(0).getCurrentWorkStatus()).isEqualTo("OFF");
        }

        @Test
        @DisplayName("실패: 대행사 정보 없는 유저 ID로 요청 — NoSuchElementException")
        void fail_noAgency_throwsException() {
            // Given — 대행사 없는 일반 유저 INSERT
            User noAgencyUser = userRepository.save(User.builder()
                    .email("noagency@test.com").passwordHash("hashed")
                    .name("무소속유저").phone("010-0000-0000")
                    .role(Role.AGENCY).build()); // agency 필드 = null

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.getAgencyEngineers(noAgencyUser.getId()))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("소속 대행사 정보가 없습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 소속 기사 상세 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 상세 조회 - getAgencyEngineerDetail")
    class GetAgencyEngineerDetail {

        @Test
        @DisplayName("성공: H2에서 실제 프로필·브랜드·지역 JOIN 조회 검증")
        void success_returnsDetailFromDb() throws Exception {
            // Given
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2015, SkillLevel.INTERMEDIATE, "냉장고 전문가");
            engineerProfileRepository.save(profile);

            expertBrandRepository.save(EngineerExpertBrand.builder()
                    .engineer(engineerUser).brandName("삼성").build());
            expertBrandRepository.save(EngineerExpertBrand.builder()
                    .engineer(engineerUser).brandName("LG").build());
            serviceRegionRepository.save(EngineerServiceRegion.builder()
                    .engineer(engineerUser).region(district).build());

            // When
            AgencyEngineerDetailResponse result =
                    agencyEngineerService.getAgencyEngineerDetail(
                            agencyUser.getId(), engineerUser.getId());

            // Then — 모든 필드가 DB에 저장된 값과 일치하는지 검증
            assertThat(result.getEngineerUserId()).isEqualTo(engineerUser.getId());
            assertThat(result.getName()).isEqualTo("테스트기사");
            assertThat(result.getEmail()).isEqualTo("engineer@test.com");
            assertThat(result.getCategoryId()).isEqualTo(leafCategory.getCategoryId());
            assertThat(result.getCategoryName()).isEqualTo("냉장고");
            assertThat(result.getCareerStartedYear()).isEqualTo(2015);
            assertThat(result.getSkillLevel()).isEqualTo("INTERMEDIATE");
            assertThat(result.getIntroduction()).isEqualTo("냉장고 전문가");
            assertThat(result.getExpertBrands()).containsExactlyInAnyOrder("삼성", "LG");
            assertThat(result.getServiceRegionIds()).contains(district.getId());
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — IllegalAccessException (DB 소속 검증)")
        void fail_otherAgencyEngineer_throwsIllegalAccess() {
            // Given — 다른 대행사 및 소속 기사 H2에 INSERT
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("다른대행사", "999-99-99999", "부산시", 3.0));
            User otherEngineer = userRepository.save(User.builder()
                    .email("other@test.com").passwordHash("hashed")
                    .name("타사기사").phone("010-0000-1111")
                    .role(Role.ENGINEER).agency(otherAgency).build());

            EngineerProfile profile = EngineerProfile.createInitial(otherEngineer);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "타사소개");
            engineerProfileRepository.save(profile);

            // When & Then — DB에 저장된 소속 대행사가 달라 접근 거부
            assertThatThrownBy(() ->
                    agencyEngineerService.getAgencyEngineerDetail(
                            agencyUser.getId(), otherEngineer.getId()))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 ID — NoSuchElementException")
        void fail_engineerNotFound() {
            // When & Then — DB에 해당 ID의 프로필이 없음
            assertThatThrownBy(() ->
                    agencyEngineerService.getAgencyEngineerDetail(agencyUser.getId(), 99999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 기사의 프로필 정보가 존재하지 않습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 소속 기사 프로필 수정
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 프로필 수정 - updateAgencyEngineerProfile")
    class UpdateAgencyEngineerProfile {

        @Test
        @DisplayName("성공: 카테고리·브랜드·지역 수정 후 DB에서 다시 조회해 반영 확인")
        void success_updateAll_verifyInDb() throws Exception {
            // Given — 초기 프로필 INSERT
            ApplianceCategory newLeaf = categoryRepository.save(
                    ApplianceCategory.createChild("세탁기", rootCategory, 2));
            Regions newDistrict = regionRepository.save(Regions.create("서초구", null, 2, 0));

            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);
            expertBrandRepository.save(EngineerExpertBrand.builder()
                    .engineer(engineerUser).brandName("삼성").build());
            serviceRegionRepository.save(EngineerServiceRegion.builder()
                    .engineer(engineerUser).region(district).build());

            AgencyEngineerProfileUpdateRequest request = buildUpdateRequest(
                    newLeaf.getCategoryId(), List.of("다이슨", "밀레"), List.of(newDistrict.getId()));

            // When
            AgencyEngineerDetailResponse result = agencyEngineerService.updateAgencyEngineerProfile(
                    agencyUser.getId(), engineerUser.getId(), request);

            // Then 1 — 반환된 DTO 검증
            assertThat(result.getCategoryId()).isEqualTo(newLeaf.getCategoryId());
            assertThat(result.getCategoryName()).isEqualTo("세탁기");
            assertThat(result.getExpertBrands()).containsExactlyInAnyOrder("다이슨", "밀레");
            assertThat(result.getServiceRegionIds()).containsExactly(newDistrict.getId());

            // Then 2 — DB에서 직접 재조회해 실제 반영 여부 확인
            EngineerProfile updatedProfile =
                    engineerProfileRepository.findByUser_Id(engineerUser.getId()).orElseThrow();
            assertThat(updatedProfile.getCategory().getCategoryId())
                    .isEqualTo(newLeaf.getCategoryId());

            List<String> brandsInDb = expertBrandRepository.findByEngineer_Id(engineerUser.getId())
                    .stream().map(EngineerExpertBrand::getBrandName).toList();
            assertThat(brandsInDb).containsExactlyInAnyOrder("다이슨", "밀레");

            List<Integer> regionsInDb = serviceRegionRepository.findByEngineer_Id(engineerUser.getId())
                    .stream().map(r -> r.getRegion().getId()).toList();
            assertThat(regionsInDb).containsExactly(newDistrict.getId());
        }

        @Test
        @DisplayName("성공: categoryId null 전달 시 기존 카테고리 유지 — DB 값 불변 확인")
        void success_categoryUnchanged_whenCategoryIdNull() throws Exception {
            // Given
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("LG"), null); // categoryId null

            // When
            AgencyEngineerDetailResponse result = agencyEngineerService.updateAgencyEngineerProfile(
                    agencyUser.getId(), engineerUser.getId(), request);

            // Then — 카테고리는 기존값 유지
            assertThat(result.getCategoryId()).isEqualTo(leafCategory.getCategoryId());
            assertThat(result.getExpertBrands()).containsExactly("LG");
        }

        @Test
        @DisplayName("실패: depth=1 카테고리 지정 — DB 저장 없이 IllegalArgumentException")
        void fail_rootCategory_throwsException() throws Exception {
            // Given
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);

            // depth=1(대분류) 카테고리 ID로 수정 요청
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(rootCategory.getCategoryId(), null, null);

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.updateAgencyEngineerProfile(
                    agencyUser.getId(), engineerUser.getId(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");

            // DB에 변경 없음을 재조회로 확인
            EngineerProfile unchanged =
                    engineerProfileRepository.findByUser_Id(engineerUser.getId()).orElseThrow();
            assertThat(unchanged.getCategory().getCategoryId())
                    .isEqualTo(leafCategory.getCategoryId());
        }

        @Test
        @DisplayName("실패: depth=1 지역(시 단위) 지정 — DB 저장 없이 IllegalArgumentException")
        void fail_cityLevelRegion_throwsException() throws Exception {
            // Given
            EngineerProfile profile = EngineerProfile.createInitial(engineerUser);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "소개글");
            engineerProfileRepository.save(profile);

            Regions cityLevel = regionRepository.save(Regions.create("서울시", null, 1, 0));
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, null, List.of(cityLevel.getId()));

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.updateAgencyEngineerProfile(
                    agencyUser.getId(), engineerUser.getId(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 수정 시도 — IllegalAccessException")
        void fail_otherAgencyEngineer_throwsIllegalAccess() throws Exception {
            // Given — 타 대행사 기사 INSERT
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("다른대행사", "000-00-00000", "인천시", 2.0));
            User otherEngineer = userRepository.save(User.builder()
                    .email("other2@test.com").passwordHash("hashed")
                    .name("타사기사2").phone("010-5555-5555")
                    .role(Role.ENGINEER).agency(otherAgency).build());
            EngineerProfile profile = EngineerProfile.createInitial(otherEngineer);
            profile.completeProfile(leafCategory, 2020, SkillLevel.BEGINNER, "타사소개");
            engineerProfileRepository.save(profile);

            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("삼성"), null);

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.updateAgencyEngineerProfile(
                    agencyUser.getId(), otherEngineer.getId(), request))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 소속 기사 월간 근무표 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("소속 기사 월간 근무표 조회 - getAgencyEngineerSchedules")
    class GetAgencyEngineerSchedules {

        @Test
        @DisplayName("성공: H2에 INSERT한 근무표 3건 — 해당 월 범위 내 조회 검증")
        void success_returnsSchedulesFromDb() throws Exception {
            // Given — 6월 근무표 2건, 7월 근무표 1건 INSERT
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineerUser).workDate(LocalDate.of(2026, 6, 3))
                    .status(ScheduleStatus.AVAILABLE).build());
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineerUser).workDate(LocalDate.of(2026, 6, 10))
                    .status(ScheduleStatus.BOOKED).build());
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineerUser).workDate(LocalDate.of(2026, 7, 1))
                    .status(ScheduleStatus.AVAILABLE).build()); // 7월 → 조회 범위 외

            // When — 2026년 6월 근무표만 조회
            List<ScheduleResponse> result = agencyEngineerService.getAgencyEngineerSchedules(
                    agencyUser.getId(), engineerUser.getId(), 2026, 6);

            // Then — 6월 2건만 반환, 7월 건은 제외
            assertThat(result).hasSize(2);
            assertThat(result).extracting(ScheduleResponse::getWorkDate)
                    .allMatch(d -> d.getMonthValue() == 6 && d.getYear() == 2026);
            assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");
            assertThat(result.get(1).getStatus()).isEqualTo("BOOKED");
        }

        @Test
        @DisplayName("성공: 근무표 없으면 빈 리스트 반환 — DB SELECT 결과 0건")
        void success_emptyList_whenNoScheduleInDb() throws Exception {
            // Given — 근무표 INSERT 없음

            // When
            List<ScheduleResponse> result = agencyEngineerService.getAgencyEngineerSchedules(
                    agencyUser.getId(), engineerUser.getId(), 2026, 6);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 근무표 날짜 오름차순 정렬 검증")
        void success_schedulesOrderedByDateAsc() throws Exception {
            // Given — 역순으로 INSERT
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineerUser).workDate(LocalDate.of(2026, 6, 20))
                    .status(ScheduleStatus.AVAILABLE).build());
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineerUser).workDate(LocalDate.of(2026, 6, 5))
                    .status(ScheduleStatus.AVAILABLE).build());

            // When
            List<ScheduleResponse> result = agencyEngineerService.getAgencyEngineerSchedules(
                    agencyUser.getId(), engineerUser.getId(), 2026, 6);

            // Then — 날짜 오름차순 정렬 확인
            assertThat(result.get(0).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 5));
            assertThat(result.get(1).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 근무표 조회 — IllegalAccessException (DB 소속 검증)")
        void fail_otherAgencyEngineer_throwsIllegalAccess() {
            // Given — 타 대행사 기사 INSERT
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("별도대행사", "111-11-11111", "대전시", 1.0));
            User otherEngineer = userRepository.save(User.builder()
                    .email("other3@test.com").passwordHash("hashed")
                    .name("타사기사3").phone("010-7777-7777")
                    .role(Role.ENGINEER).agency(otherAgency).build());

            // When & Then
            assertThatThrownBy(() -> agencyEngineerService.getAgencyEngineerSchedules(
                    agencyUser.getId(), otherEngineer.getId(), 2026, 6))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("소속 대행사의 기사만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 ID — NoSuchElementException")
        void fail_engineerNotFound() {
            // When & Then — DB에 해당 userId 없음
            assertThatThrownBy(() -> agencyEngineerService.getAgencyEngineerSchedules(
                    agencyUser.getId(), 99999L, 2026, 6))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 기사 정보가 존재하지 않습니다.");
        }
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private AgencyEngineerProfileUpdateRequest buildUpdateRequest(
            Integer categoryId, List<String> brands, List<Integer> regionIds) {
        try {
            Constructor<AgencyEngineerProfileUpdateRequest> ctor =
                    AgencyEngineerProfileUpdateRequest.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AgencyEngineerProfileUpdateRequest req = ctor.newInstance();
            ReflectionTestUtils.setField(req, "categoryId", categoryId);
            ReflectionTestUtils.setField(req, "expertBrands", brands);
            ReflectionTestUtils.setField(req, "serviceRegionIds", regionIds);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
