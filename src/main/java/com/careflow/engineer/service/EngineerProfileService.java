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
    public ProfileResponse updateProfile(Long userId, CreateProfileRequest request){    // 프로필 작성
        User user = userRepository.findById(userId) // 유저 조회
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if(user.getRole() != Role.ENGINEER){    // 수리기사 권한인지
            throw new IllegalArgumentException("수리기사 권한 가진 계정만 프로필을 생성할 수 있습니다.");
        }

        EngineerProfile profile = profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("대행사 승인이 완료되지 않은 기사입니다."));

        if(profile.getCategory() != null && profile.getCareerStartedYear() != null){
            throw new IllegalArgumentException("이미 프로필 등록을 완료한 기사입니다.");
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

        profile.completeProfile(category, request.getCareerStartedYear(), calculatedSkillLevel, request.getIntroduction());

        EngineerProfile savedProfile = profileRepository.save(profile);

        return ProfileResponse.from(savedProfile);
    }

    private SkillLevel calculateSkillLevel(Integer careerStartedYear) { // 연차별 등급 산정
        if(careerStartedYear == null) {
            throw new IllegalArgumentException("경력 시작 연도가 필요합니다.");
        }

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
