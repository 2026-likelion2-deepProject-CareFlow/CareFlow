package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.ProfileResponse;
import com.careflow.engineer.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EngineerProfileServiceTest {

    @InjectMocks
    private EngineerProfileService engineerProfileService;

    @Mock
    private EngineerProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplianceCategoryRepository categoryRepository;

    @Test
    @DisplayName("기사 프로필 완성 성공 (10년차 -> INTERMEDIATE 등급 반영)")
    void updateProfile_Success() throws Exception {
        // Given (준비)
        Long userId = 1L;
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", userId); // User 엔티티의 PK 필드명 id로 매핑

        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", 10); // Integer 타입
        ReflectionTestUtils.setField(category, "depth", 2);

        EngineerProfile emptyProfile = EngineerProfile.builder().user(user).build();

        // [핵심] protected 생성자 리플렉션으로 뚫기!
        Constructor<CreateProfileRequest> reqConstructor = CreateProfileRequest.class.getDeclaredConstructor();
        reqConstructor.setAccessible(true);
        CreateProfileRequest request = reqConstructor.newInstance();

        ReflectionTestUtils.setField(request, "categoryId", 10);

        int tenYearsAgo = LocalDate.now().getYear() - 10 + 1;
        ReflectionTestUtils.setField(request, "careerStartedYear", tenYearsAgo);
        ReflectionTestUtils.setField(request, "introduction", "안녕하세요");

        // Mock 세팅 (팀원분이 수정한 findByUser_Id 적용)
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(1L)).willReturn(Optional.of(emptyProfile));
        given(categoryRepository.findById(10)).willReturn(Optional.of(category));

        // When (실행)
        ProfileResponse response = engineerProfileService.updateProfile(1L, request);

        // Then (검증)
        assertThat(response.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE.name());
        assertThat(response.getIntroduction()).isEqualTo("안녕하세요");
        assertThat(emptyProfile.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("실패: 이미 프로필을 완성한 기사인 경우 예외 발생")
    void updateProfile_Fail_AlreadyCompleted() throws Exception {
        // Given
        Long userId = 1L;
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", userId);

        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", 10);
        ReflectionTestUtils.setField(category, "depth", 2);

        EngineerProfile completedProfile = EngineerProfile.builder()
                .user(user)
                .category(category)
                .careerStartedYear(2020)
                .skillLevel(SkillLevel.BEGINNER)
                .build();

        // protected 생성자 우회
        Constructor<CreateProfileRequest> reqConstructor = CreateProfileRequest.class.getDeclaredConstructor();
        reqConstructor.setAccessible(true);
        CreateProfileRequest request = reqConstructor.newInstance();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUser_Id(1L)).willReturn(Optional.of(completedProfile));

        // When & Then
        assertThatThrownBy(() -> engineerProfileService.updateProfile(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 프로필 등록을 완료한 기사입니다.");
    }
}