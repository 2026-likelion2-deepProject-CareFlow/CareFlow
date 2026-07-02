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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerProfileService {

    private final EngineerProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ApplianceCategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final EngineerServiceRegionRepository serviceRegionRepository;
    private final EngineerExpertBrandRepository expertBrandRepository;

    /**
     * 기사 첫 로그인 시 프로필 완성.
     * 1) 승인 직후 생성된 빈 프로필을 조회해 카테고리·경력·등급·소개 채움
     * 2) 전문 브랜드 / 서비스 지역을 "전체 삭제 후 재INSERT" 패턴으로 갱신
     */
    @Transactional
    public ProfileResponse completeProfile(Long userId, CreateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if (user.getRole() != Role.ENGINEER) {
            throw new IllegalArgumentException("수리기사 권한을 가진 계정만 프로필을 작성할 수 있습니다.");
        }

        EngineerProfile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("대행사 승인이 완료되지 않은 기사입니다."));

        if (profile.isCompleted()) {
            throw new IllegalArgumentException("이미 프로필 작성을 완료한 기사입니다.");
        }

        // 카테고리 검증: 소분류(depth=2)만 허용
        ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("전문 분야 정보가 존재하지 않습니다."));
        if (category.getDepth() != 2) {
            throw new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
        }

        // 경력 연도 검증 (미래 불가) 후 등급 산정
        int currentYear = LocalDate.now().getYear();
        if (request.getCareerStartedYear() > currentYear) {
            throw new IllegalArgumentException("경력 시작 연도는 미래일 수 없습니다.");
        }
        SkillLevel skillLevel = calculateSkillLevel(request.getCareerStartedYear());

        profile.completeProfile(category, request.getCareerStartedYear(), skillLevel, request.getIntroduction());
        EngineerProfile saved = profileRepository.save(profile);

        // 서비스 지역 갱신 (depth=2 구 단위만 허용)
        List<Integer> regionIds = saveServiceRegions(user, request.getServiceRegionIds());
        // 전문 브랜드 갱신
        List<String> brandNames = saveExpertBrands(user, request.getExpertBrands());

// 🌟 수정: 프론트엔드 응답용 지역 이름 리스트 조회
        List<String> regionNames = serviceRegionRepository.findByEngineer_Id(userId).stream()
                .map(r -> r.getRegion().getName())
                .toList();

        // 🌟 수정: .from -> .of 로 변경하고 지역 이름 리스트 추가
        return ProfileResponse.of(saved, brandNames, regionIds, regionNames);
    }

    private List<Integer> saveServiceRegions(User user, List<Integer> serviceRegionIds) {
        serviceRegionRepository.deleteByEngineer_Id(user.getId());

        List<EngineerServiceRegion> entities = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (Integer regionId : serviceRegionIds.stream().distinct().toList()) {
            Regions region = regionRepository.findById(regionId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 지역입니다. (regionId=" + regionId + ")"));
            if (region.getDepth() != 2) {
                throw new IllegalArgumentException("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
            }
            entities.add(EngineerServiceRegion.builder().engineer(user).region(region).build());
            result.add(regionId);
        }
        serviceRegionRepository.saveAll(entities);
        return result;
    }

    private List<String> saveExpertBrands(User user, List<String> expertBrands) {
        expertBrandRepository.deleteByEngineer_Id(user.getId());

        List<String> distinctBrands = expertBrands.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        List<EngineerExpertBrand> entities = distinctBrands.stream()
                .map(b -> EngineerExpertBrand.builder().engineer(user).brandName(b).build())
                .toList();
        expertBrandRepository.saveAll(entities);
        return distinctBrands;
    }

    private SkillLevel calculateSkillLevel(Integer careerStartedYear) { // 연차별 등급 산정
        if (careerStartedYear == null) {
            throw new IllegalArgumentException("경력 시작 연도가 필요합니다.");
        }
        int workYear = LocalDate.now().getYear() - careerStartedYear; // 1~5=초급, 6~10=중급, 11↑=고급
        if (workYear <= 5) {
            return SkillLevel.BEGINNER;
        } else if (workYear <= 10) {
            return SkillLevel.INTERMEDIATE;
        } else {
            return SkillLevel.ADVANCED;
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {    // 프로필 상세 조회
        EngineerProfile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 정보가 존재하지 않습니다."));

        List<String> expertBrands = expertBrandRepository.findByEngineer_Id(userId).stream()
                .map(EngineerExpertBrand::getBrandName)
                .toList();

        // 🌟 프론트엔드를 위해 지역 ID와 지역 이름을 동시에 추출
        List<EngineerServiceRegion> regions = serviceRegionRepository.findByEngineer_Id(userId);

        List<Integer> serviceRegionIds = regions.stream()
                .map(r -> r.getRegion().getId())
                .toList();

        List<String> serviceRegionNames = regions.stream()
                .map(r -> r.getRegion().getName()) // ex) "강남구"
                .toList();

        // from 대신 우리가 새로 만든 of 메서드 사용!
        return ProfileResponse.of(profile, expertBrands, serviceRegionIds, serviceRegionNames);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {   // 프로필 정보 수정
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        EngineerProfile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 정보가 존재하지 않습니다."));

        SkillLevel newSkillLevel = profile.getSkillLevel();
        if (request.getCareerStartedYear() != null) {
            int currentYear = java.time.LocalDate.now().getYear();
            if (request.getCareerStartedYear() > currentYear) {
                throw new IllegalArgumentException("경력 시작 연도는 미래일 수 없습니다.");
            }
            newSkillLevel = calculateSkillLevel(request.getCareerStartedYear());
        }

        // 엔티티 업데이트 (더티 체킹)
        profile.updateBasicInfo(request.getCareerStartedYear(), newSkillLevel, request.getIntroduction(), request.getProfileImageUrl());

        List<String> brandNames = expertBrandRepository.findByEngineer_Id(userId).stream()
                .map(EngineerExpertBrand::getBrandName).toList();

        List<EngineerServiceRegion> currentRegions = serviceRegionRepository.findByEngineer_Id(userId);
        List<Integer> regionIds = currentRegions.stream().map(r -> r.getRegion().getId()).toList();
        List<String> regionNames = currentRegions.stream().map(r -> r.getRegion().getName()).toList();

        if (request.getExpertBrands() != null && !request.getExpertBrands().isEmpty()) {
            brandNames = saveExpertBrands(user, request.getExpertBrands());
        }
        if (request.getServiceRegionIds() != null && !request.getServiceRegionIds().isEmpty()) {
            regionIds = saveServiceRegions(user, request.getServiceRegionIds());

            // 🌟 수정: 지역 ID가 변경되었으므로, DB에서 새로운 지역 이름들을 다시 읽어옵니다.
            regionNames = serviceRegionRepository.findByEngineer_Id(userId).stream()
                    .map(r -> r.getRegion().getName())
                    .toList();
        }

        // 🌟 수정: .from -> .of 로 변경하고 지역 이름 리스트 추가
        return ProfileResponse.of(profile, brandNames, regionIds, regionNames);
    }

    @Transactional(readOnly = true)
    public com.careflow.engineer.dto.EngineerNavbarResponse getNavbarProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        // 프로필은 미등록 상태일 수도 있으므로 Optional 처리
        EngineerProfile profile = profileRepository.findByUser_Id(userId).orElse(null);

        return com.careflow.engineer.dto.EngineerNavbarResponse.builder()
                .name(user.getName())
                .role("수리 기사")
                .profileImageUrl(profile != null ? profile.getProfileImageUrl() : null)
                .build();
    }
}