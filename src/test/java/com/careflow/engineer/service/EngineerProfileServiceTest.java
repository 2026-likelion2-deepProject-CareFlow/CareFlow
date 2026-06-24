package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.ProfileResponse;
import com.careflow.engineer.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EngineerProfileServiceTest {

    @InjectMocks
    private EngineerProfileService engineerProfileService;

    @Mock private EngineerProfileRepository profileRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplianceCategoryRepository categoryRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private EngineerServiceRegionRepository serviceRegionRepository;
    @Mock private EngineerExpertBrandRepository expertBrandRepository;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("성공: 프로필 완성 — 10년차→INTERMEDIATE, 브랜드·서비스 지역까지 함께 저장")
    void completeProfile_Success() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile emptyProfile = EngineerProfile.createInitial(user);
        ApplianceCategory category = category(10, 2);
        Regions district = region(2); // depth=2 구 단위

        int tenYearsAgo = LocalDate.now().getYear() - 10 + 1; // workYear=10 → INTERMEDIATE
        CreateProfileRequest request = newRequest(
                10, tenYearsAgo, "안녕하세요",
                List.of("삼성", "LG"), List.of(10));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(emptyProfile));
        given(categoryRepository.findById(10)).willReturn(Optional.of(category));
        given(regionRepository.findById(10)).willReturn(Optional.of(district));
        given(profileRepository.save(any(EngineerProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        ProfileResponse response = engineerProfileService.completeProfile(USER_ID, request);

        // Then
        assertThat(response.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE.name());
        assertThat(response.getIntroduction()).isEqualTo("안녕하세요");
        assertThat(response.getExpertBrands()).containsExactly("삼성", "LG");
        assertThat(response.getServiceRegionIds()).containsExactly(10);
        assertThat(emptyProfile.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("실패: 이미 프로필을 완성한 기사")
    void completeProfile_Fail_AlreadyCompleted() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile completed = EngineerProfile.createInitial(user);
        completed.completeProfile(category(10, 2), 2020, SkillLevel.BEGINNER, null);

        CreateProfileRequest request = newRequest(10, 2020, null, List.of("삼성"), List.of(10));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(completed));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.completeProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 프로필 작성을 완료한 기사입니다.");
    }

    @Test
    @DisplayName("실패: ENGINEER 권한이 아닌 계정")
    void completeProfile_Fail_NotEngineer() throws Exception {
        // Given
        User customer = User.builder().role(Role.CUSTOMER).build();
        ReflectionTestUtils.setField(customer, "id", USER_ID);

        CreateProfileRequest request = newRequest(10, 2020, null, List.of("삼성"), List.of(10));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(customer));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.completeProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수리기사 권한을 가진 계정만 프로필을 작성할 수 있습니다.");
    }

    @Test
    @DisplayName("실패: 전문 분야가 소분류(depth=2)가 아님")
    void completeProfile_Fail_CategoryNotLeaf() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile emptyProfile = EngineerProfile.createInitial(user);
        ApplianceCategory bigCategory = category(1, 1); // depth=1 대분류

        CreateProfileRequest request = newRequest(1, 2020, null, List.of("삼성"), List.of(10));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(emptyProfile));
        given(categoryRepository.findById(1)).willReturn(Optional.of(bigCategory));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.completeProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
    }

    @Test
    @DisplayName("실패: 경력 시작 연도가 미래")
    void completeProfile_Fail_FutureCareerYear() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile emptyProfile = EngineerProfile.createInitial(user);
        ApplianceCategory category = category(10, 2);

        int futureYear = LocalDate.now().getYear() + 1;
        CreateProfileRequest request = newRequest(10, futureYear, null, List.of("삼성"), List.of(10));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(emptyProfile));
        given(categoryRepository.findById(10)).willReturn(Optional.of(category));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.completeProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("경력 시작 연도는 미래일 수 없습니다.");
    }

    @Test
    @DisplayName("실패: 서비스 지역이 구 단위(depth=2)가 아님")
    void completeProfile_Fail_ServiceRegionNotDistrict() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile emptyProfile = EngineerProfile.createInitial(user);
        ApplianceCategory category = category(10, 2);
        Regions city = region(1); // depth=1 시·도

        CreateProfileRequest request = newRequest(10, 2020, null, List.of("삼성"), List.of(99));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(emptyProfile));
        given(categoryRepository.findById(10)).willReturn(Optional.of(category));
        given(profileRepository.save(any(EngineerProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(regionRepository.findById(99)).willReturn(Optional.of(city));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.completeProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
    }

    // ---------- 픽스처 헬퍼 ----------

    private User engineer(Long id) {
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ApplianceCategory category(int categoryId, int depth) {
        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", categoryId);
        ReflectionTestUtils.setField(category, "depth", depth);
        return category;
    }

    private Regions region(int depth) {
        return Regions.builder().name("테스트지역").parentId(null).depth(depth).sortOrder(0).build();
    }

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

    // ─────────────────────────────────────────────
    //  조회 및 수정 로직 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("성공: 프로필 상세 조회 (getProfile)")
    void getProfile_Success() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile profile = EngineerProfile.createInitial(user);
        ApplianceCategory category = category(10, 2);
        profile.completeProfile(category, 2020, SkillLevel.BEGINNER, "안녕하세요 수리기사입니다.");

        // 모킹용 연관 데이터 생성
        EngineerExpertBrand brand1 = EngineerExpertBrand.builder().engineer(user).brandName("삼성").build();
        EngineerExpertBrand brand2 = EngineerExpertBrand.builder().engineer(user).brandName("LG").build();

        Regions regionMock = region(2);
        ReflectionTestUtils.setField(regionMock, "id", 10);
        EngineerServiceRegion serviceRegion = EngineerServiceRegion.builder().engineer(user).region(regionMock).build();

        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(profile));
        given(expertBrandRepository.findByEngineer_Id(USER_ID)).willReturn(List.of(brand1, brand2));
        given(serviceRegionRepository.findByEngineer_Id(USER_ID)).willReturn(List.of(serviceRegion));

        // When
        ProfileResponse response = engineerProfileService.getProfile(USER_ID);

        // Then
        assertThat(response.getIntroduction()).isEqualTo("안녕하세요 수리기사입니다.");
        assertThat(response.getExpertBrands()).containsExactly("삼성", "LG");
        assertThat(response.getServiceRegionIds()).containsExactly(10);
    }

    @Test
    @DisplayName("성공: 프로필 정보 수정 (updateProfile)")
    void updateProfile_Success() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile profile = EngineerProfile.createInitial(user);
        profile.completeProfile(category(10, 2), 2020, SkillLevel.BEGINNER, "기존 소개글");

        // DTO 리플렉션 생성 (기존 헬퍼 방식 활용)
        Constructor<com.careflow.engineer.dto.UpdateProfileRequest> constructor =
                com.careflow.engineer.dto.UpdateProfileRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        com.careflow.engineer.dto.UpdateProfileRequest request = constructor.newInstance();
        ReflectionTestUtils.setField(request, "introduction", "수정된 소개글");
        ReflectionTestUtils.setField(request, "profileImageUrl", "http://new-image.com");
        ReflectionTestUtils.setField(request, "expertBrands", List.of("다이슨"));
        ReflectionTestUtils.setField(request, "serviceRegionIds", List.of(99));

        Regions regionMock = region(2);
        ReflectionTestUtils.setField(regionMock, "id", 99);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(profile));
        // 기존 데이터가 없다고 가정하고 새로운 데이터 삽입 로직 테스트
        given(expertBrandRepository.findByEngineer_Id(USER_ID)).willReturn(List.of());
        given(serviceRegionRepository.findByEngineer_Id(USER_ID)).willReturn(List.of());
        given(regionRepository.findById(99)).willReturn(Optional.of(regionMock));

        // When
        ProfileResponse response = engineerProfileService.updateProfile(USER_ID, request);

        // Then
        assertThat(response.getIntroduction()).isEqualTo("수정된 소개글");
        assertThat(response.getProfileImageUrl()).isEqualTo("http://new-image.com");
        assertThat(response.getExpertBrands()).containsExactly("다이슨");
        assertThat(response.getServiceRegionIds()).containsExactly(99);
    }
}