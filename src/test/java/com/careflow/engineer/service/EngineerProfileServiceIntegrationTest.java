package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.ProfileResponse;
import com.careflow.engineer.dto.UpdateProfileRequest;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerProfileService 통합 테스트 (진짜 DB 연동)")
class EngineerProfileServiceIntegrationTest {

    @Autowired private EngineerProfileService engineerProfileService;
    @Autowired private EngineerProfileRepository profileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EngineerServiceRegionRepository serviceRegionRepository;
    @Autowired private EngineerExpertBrandRepository expertBrandRepository;

    private User testUser;
    private ApplianceCategory testCategory;
    private Regions testRegion;

    @BeforeEach
    void setUp() {
        // Mock이 아니라 진짜 DB(H2)에 기초 데이터를 INSERT 합니다.
        testUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("테스트기사").phone("010-1234-5678").role(Role.ENGINEER).build());

        // v5 신규 API: createRoot → createChild 2단계 생성
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("테스트대분류", 1));
        testCategory = categoryRepository.save(ApplianceCategory.createChild("테스트소분류", rootCat, 1));

        testRegion = regionRepository.save(Regions.create("테스트구", null, 2, 0));
    }

    @Test
    @DisplayName("성공: 프로필 완성 (DB Insert 검증)")
    void completeProfile_Success() throws Exception {
        // Given
        profileRepository.save(EngineerProfile.createInitial(testUser));
        int tenYearsAgo = LocalDate.now().getYear() - 10 + 1;

        CreateProfileRequest request = newRequest(testCategory.getCategoryId(), tenYearsAgo, "안녕하세요",
                List.of("삼성", "LG"), List.of(testRegion.getId()));

        // When
        ProfileResponse response = engineerProfileService.completeProfile(testUser.getId(), request);

        // Then
        assertThat(response.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE.name());
        assertThat(response.getExpertBrands()).containsExactlyInAnyOrder("삼성", "LG");

        // 실제 DB에서 잘 바뀌었는지 다시 조회해서 검증!
        EngineerProfile savedProfile = profileRepository.findByUser_Id(testUser.getId()).orElseThrow();
        assertThat(savedProfile.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("성공: 프로필 상세 조회 및 수정 (DB Select & Update 검증)")
    void getAndUpdateProfile_Success() throws Exception {
        // Given (초기 세팅)
        EngineerProfile profile = EngineerProfile.createInitial(testUser);
        profile.completeProfile(testCategory, 2020, SkillLevel.BEGINNER, "기존 소개글");
        profileRepository.save(profile);

        expertBrandRepository.save(EngineerExpertBrand.builder().engineer(testUser).brandName("삼성").build());
        serviceRegionRepository.save(EngineerServiceRegion.builder().engineer(testUser).region(testRegion).build());

        // 1. GetProfile (조회 테스트)
        ProfileResponse getResponse = engineerProfileService.getProfile(testUser.getId());
        assertThat(getResponse.getIntroduction()).isEqualTo("기존 소개글");
        assertThat(getResponse.getExpertBrands()).contains("삼성");

        // 2. UpdateProfile (수정 테스트)
        Regions newRegion = regionRepository.save(Regions.create("새로운구", null, 2, 0));
        UpdateProfileRequest updateReq = newUpdateRequest("수정된 소개글", "http://new.jpg", List.of("다이슨"), List.of(newRegion.getId()));

        ProfileResponse updateResponse = engineerProfileService.updateProfile(testUser.getId(), updateReq);

        // Then
        assertThat(updateResponse.getIntroduction()).isEqualTo("수정된 소개글");
        assertThat(updateResponse.getExpertBrands()).containsExactly("다이슨");
        assertThat(updateResponse.getServiceRegionIds()).containsExactly(newRegion.getId());
    }

    // --- 헬퍼 메서드 ---
    private CreateProfileRequest newRequest(Integer categoryId, Integer careerStartedYear, String introduction,
                                            List<String> expertBrands, List<Integer> serviceRegionIds) throws Exception {
        Constructor<CreateProfileRequest> constructor = CreateProfileRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreateProfileRequest request = constructor.newInstance();
        ReflectionTestUtils.setField(request, "categoryId", categoryId);
        ReflectionTestUtils.setField(request, "careerStartedYear", careerStartedYear);
        ReflectionTestUtils.setField(request, "introduction", introduction);
        ReflectionTestUtils.setField(request, "expertBrands", expertBrands);
        ReflectionTestUtils.setField(request, "serviceRegionIds", serviceRegionIds);
        return request;
    }

    private UpdateProfileRequest newUpdateRequest(String intro, String imgUrl, List<String> brands, List<Integer> regions) throws Exception {
        Constructor<UpdateProfileRequest> constructor = UpdateProfileRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        UpdateProfileRequest req = constructor.newInstance();
        ReflectionTestUtils.setField(req, "introduction", intro);
        ReflectionTestUtils.setField(req, "profileImageUrl", imgUrl);
        ReflectionTestUtils.setField(req, "expertBrands", brands);
        ReflectionTestUtils.setField(req, "serviceRegionIds", regions);
        return req;
    }

    // =========================================================================
    // 💡 추가된 [프로필 경력 연도 수정 -> 등급 재산정] 통합 테스트
    // =========================================================================

    @Test
    @DisplayName("성공: 통합 환경에서 프로필 경력 연도 수정 시 등급이 재산정되어 DB에 반영된다")
    void updateProfile_RecalculateSkillLevel_Integration() throws Exception {
        // Given: 기존 프로필 세팅 (최초 가입 시 1년차 BEGINNER로 세팅되었다고 가정)
        EngineerProfile profile = EngineerProfile.createInitial(testUser);
        int currentYear = LocalDate.now().getYear();
        profile.completeProfile(testCategory, currentYear, SkillLevel.BEGINNER, "신입입니다");
        profileRepository.save(profile);

        // When: 8년 전(중급, INTERMEDIATE)으로 경력을 수정하는 요청 발생
        int intermediateYear = currentYear - 8;
        Constructor<UpdateProfileRequest> constructor = UpdateProfileRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        UpdateProfileRequest updateReq = constructor.newInstance();
        ReflectionTestUtils.setField(updateReq, "careerStartedYear", intermediateYear);

        engineerProfileService.updateProfile(testUser.getId(), updateReq);

        // Then: DB에서 다시 조회했을 때 연도와 등급이 모두 완벽하게 바뀌어 있어야 함
        EngineerProfile updatedProfile = profileRepository.findByUser_Id(testUser.getId()).orElseThrow();
        assertThat(updatedProfile.getCareerStartedYear()).isEqualTo(intermediateYear);
        assertThat(updatedProfile.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE);
    }

    @Test
    @DisplayName("성공: 기존 브랜드·지역과 겹치는 값으로 재수정해도 uk_eng_brand/uk_eng_region 위반 없이 성공 " +
            "(PUT /api/engineer/profile 409 Duplicate entry 회귀 방지)")
    void updateProfile_overlappingBrandsAndRegions_noDuplicateKeyError() throws Exception {
        // Given — 기존에 "삼성","LG" / testRegion 저장, 새 목록에도 "삼성" / testRegion이 그대로 포함됨
        EngineerProfile profile = EngineerProfile.createInitial(testUser);
        profile.completeProfile(testCategory, 2020, SkillLevel.BEGINNER, "소개글");
        profileRepository.save(profile);
        expertBrandRepository.save(EngineerExpertBrand.builder().engineer(testUser).brandName("삼성").build());
        expertBrandRepository.save(EngineerExpertBrand.builder().engineer(testUser).brandName("LG").build());
        serviceRegionRepository.save(EngineerServiceRegion.builder().engineer(testUser).region(testRegion).build());
        Regions newRegion = regionRepository.save(Regions.create("겹치지않는구", null, 2, 0));

        UpdateProfileRequest request = newUpdateRequest(
                null, null, List.of("삼성", "위니아"), List.of(testRegion.getId(), newRegion.getId()));

        // When & Then — EngineerExpertBrand/EngineerServiceRegion은 GenerationType.IDENTITY라
        // flush 없이 delete 후 재삽입하면 겹치는 값에서 유니크 제약 위반이 발생했었음
        ProfileResponse response = engineerProfileService.updateProfile(testUser.getId(), request);

        assertThat(response.getExpertBrands()).containsExactlyInAnyOrder("삼성", "위니아");
        assertThat(response.getServiceRegionIds()).containsExactlyInAnyOrder(testRegion.getId(), newRegion.getId());

        List<String> brandsInDb = expertBrandRepository.findByEngineer_Id(testUser.getId())
                .stream().map(EngineerExpertBrand::getBrandName).toList();
        assertThat(brandsInDb).containsExactlyInAnyOrder("삼성", "위니아");
    }
}