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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerProfileService {
    private final EngineerProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ApplianceCategoryRepository categoryRepository;

    @Transactional
    public ProfileResponse createProfile(Long userId, CreateProfileRequest request){    // 프로필 생성
        User user = userRepository.findById(userId) // 유저 조회
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if(user.getRole() != Role.ENGINEER){    // 수리기사 권한인지
            throw new IllegalArgumentException("수리기사 권한 가진 계정만 프로필을 생성할 수 있습니다.");
        }

        if(profileRepository.existsByUserId(userId)) { // 중복 가입 방지
            throw new IllegalArgumentException("유저 프로필 정보가 이미 존재합니다.");
        }

        // 카테고리 조회
        ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(()-> new IllegalArgumentException("전문 분야 정보가 존재하지 않습니다."));

        // 소분류가 맞는지 확인
        if(category.getDepth() != 2) {
            throw new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
        }

        // 연차 및 등급 산정
        SkillLevel calculatedSkillLevel = calculateSkillLevel(request.getCareerStartedYear());

        EngineerProfile newProfile = EngineerProfile.builder()
                .user(user)
                .category(category)
                .careerStartedYear(request.getCareerStartedYear())
                .skillLevel(calculatedSkillLevel)
                .introduction(request.getIntroduction())
                .build();

        EngineerProfile savedProfile = profileRepository.save(newProfile);

        return ProfileResponse.from(savedProfile);
    }

    private SkillLevel calculateSkillLevel(int careerStartedYear) { // 연차별 등급 산정
        int workYear = LocalDate.now().getYear() - careerStartedYear + 1;

        if (workYear <= 5) {
            return SkillLevel.BEGINNER;
        } else if (workYear <= 10) {
            return SkillLevel.INTERMEDIATE;
        } else {
            return SkillLevel.ADVANCED;
        }
    }
}
