package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
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
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgencyEngineerService {

    private final UserRepository userRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final EngineerExpertBrandRepository expertBrandRepository;
    private final EngineerServiceRegionRepository serviceRegionRepository;
    private final EngineerScheduleRepository engineerScheduleRepository;
    private final ApplianceCategoryRepository categoryRepository;
    private final RegionRepository regionRepository;

    /**
     * 소속 기사 목록 조회
     * 로그인한 대행사 관리자의 agencyId 기준으로 소속 기사 전체를 조회한다.
     * 기사별 오늘 날짜 근무 상태(AVAILABLE/BOOKED/OFF)를 함께 반환한다.
     */
    public List<AgencyEngineerSummaryResponse> getAgencyEngineers(Long agencyUserId) {
        // 로그인한 유저의 소속 대행사 조회
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        List<EngineerProfile> profiles = engineerProfileRepository.findByAgencyId(agencyId);

        LocalDate today = LocalDate.now();

        return profiles.stream()
                .map(profile -> {
                    Long engineerUserId = profile.getUser().getId();

                    List<Integer> regionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                            .map(r -> r.getRegion().getId())
                            .toList();

                    // 오늘 근무표가 있으면 해당 상태, 없으면 OFF로 표시
                    String currentStatus = engineerScheduleRepository
                            .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerUserId, today, today)
                            .stream()
                            .findFirst()
                            .map(s -> s.getStatus().name())
                            .orElse(ScheduleStatus.OFF.name());

                    return AgencyEngineerSummaryResponse.from(profile, regionIds, currentStatus);
                })
                .toList();
    }

    /**
     * 소속 기사 단건 상세 조회
     * 타 대행사 소속 기사 접근 시 IllegalAccessException 발생
     */
    public AgencyEngineerDetailResponse getAgencyEngineerDetail(Long agencyUserId, Long engineerUserId) throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

        // 소속 대행사 일치 검증
        validateSameAgency(profile, agencyId);

        List<String> expertBrands = expertBrandRepository.findByEngineer_Id(engineerUserId).stream()
                .map(EngineerExpertBrand::getBrandName)
                .toList();

        List<Integer> regionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                .map(r -> r.getRegion().getId())
                .toList();

        return AgencyEngineerDetailResponse.from(profile, expertBrands, regionIds);
    }

    /**
     * 소속 기사 프로필 수정 (대행사 관리자 권한)
     * 수정 가능 항목: 전문 가전 카테고리, 전문 브랜드 목록, 활동 지역 목록
     * null로 전달된 필드는 기존값 유지
     */
    @Transactional
    public AgencyEngineerDetailResponse updateAgencyEngineerProfile(
            Long agencyUserId, Long engineerUserId, AgencyEngineerProfileUpdateRequest request) throws IllegalAccessException {

        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

        validateSameAgency(profile, agencyId);

        User engineerUser = profile.getUser();

        // 카테고리 수정 — depth=2(소분류)만 허용
        if (request.getCategoryId() != null) {
            ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다. (categoryId=" + request.getCategoryId() + ")"));
            if (category.getDepth() != 2) {
                throw new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
            }
            profile.updateCategory(category);
        }

        // 전문 브랜드 수정 (전체 교체)
        List<String> resultBrands = expertBrandRepository.findByEngineer_Id(engineerUserId).stream()
                .map(EngineerExpertBrand::getBrandName).toList();
        if (request.getExpertBrands() != null && !request.getExpertBrands().isEmpty()) {
            resultBrands = saveExpertBrands(engineerUser, request.getExpertBrands());
        }

        // 활동 지역 수정 (전체 교체, depth=2 구 단위만 허용)
        List<Integer> resultRegionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                .map(r -> r.getRegion().getId()).toList();
        if (request.getServiceRegionIds() != null && !request.getServiceRegionIds().isEmpty()) {
            resultRegionIds = saveServiceRegions(engineerUser, request.getServiceRegionIds());
        }

        return AgencyEngineerDetailResponse.from(profile, resultBrands, resultRegionIds);
    }

    /**
     * 소속 기사 월간 근무표 조회
     * year/month 기준 해당 기사의 근무 일정 전체 반환
     */
    public List<ScheduleResponse> getAgencyEngineerSchedules(Long agencyUserId, Long engineerUserId, int year, int month) throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        // 대상 기사 존재 여부 및 소속 검증
        User engineerUser = userRepository.findById(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사 정보가 존재하지 않습니다."));

        if (engineerUser.getAgency() == null || !engineerUser.getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("소속 대행사의 기사만 조회할 수 있습니다.");
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        return engineerScheduleRepository
                .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerUserId, startDate, endDate)
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    // ──────────────────────────────────────────────────
    // Private helper methods
    // ──────────────────────────────────────────────────

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("유저 정보가 존재하지 않습니다."));
    }

    private Long getAgencyId(User user) {
        if (user.getAgency() == null) {
            throw new NoSuchElementException("소속 대행사 정보가 없습니다.");
        }
        return user.getAgency().getId();
    }

    // 대상 기사가 동일 대행사 소속인지 검증
    private void validateSameAgency(EngineerProfile profile, Long agencyId) throws IllegalAccessException {
        if (profile.getUser().getAgency() == null
                || !profile.getUser().getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }
    }

    private List<String> saveExpertBrands(User engineer, List<String> brands) {
        expertBrandRepository.deleteByEngineer_Id(engineer.getId());

        List<String> distinctBrands = brands.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        List<EngineerExpertBrand> entities = distinctBrands.stream()
                .map(b -> EngineerExpertBrand.builder().engineer(engineer).brandName(b).build())
                .toList();
        expertBrandRepository.saveAll(entities);
        return distinctBrands;
    }

    private List<Integer> saveServiceRegions(User engineer, List<Integer> regionIds) {
        serviceRegionRepository.deleteByEngineer_Id(engineer.getId());

        List<EngineerServiceRegion> entities = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (Integer regionId : regionIds.stream().distinct().toList()) {
            Regions region = regionRepository.findById(regionId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 지역입니다. (regionId=" + regionId + ")"));
            if (region.getDepth() != 2) {
                throw new IllegalArgumentException("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
            }
            entities.add(EngineerServiceRegion.builder().engineer(engineer).region(region).build());
            result.add(regionId);
        }
        serviceRegionRepository.saveAll(entities);
        return result;
    }
}
