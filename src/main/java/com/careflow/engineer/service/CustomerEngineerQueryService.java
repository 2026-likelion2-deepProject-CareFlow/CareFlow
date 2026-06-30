package com.careflow.engineer.service;

import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.dto.CustomerEngineerAvailabilityResponse;
import com.careflow.engineer.dto.CustomerEngineerSummaryResponse;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 고객용 수동 배정 - 기사 조회 서비스
 * 대행사 1곳에 국한하지 않고 전체 소속 기사를 대상으로 조회한다는 점이 AgencyEngineerService 와 다르다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerEngineerQueryService {

    private final EngineerProfileRepository engineerProfileRepository;
    private final EngineerExpertBrandRepository expertBrandRepository;
    private final EngineerScheduleRepository engineerScheduleRepository;
    private final RegionRepository regionRepository;

    /**
     * 조건(지역/브랜드/보유기술) 필터링된 후보 기사 목록 조회
     */
    public List<CustomerEngineerSummaryResponse> getAvailableEngineers(Integer regionId, String brand, String skill) {
        if (!regionRepository.existsById(regionId)) {
            throw new NoSuchElementException("존재하지 않는 지역입니다.");
        }

        List<EngineerProfile> candidates =
                engineerProfileRepository.findAvailableForCustomer(regionId, brand, skill);

        return candidates.stream()
                .map(profile -> {
                    List<String> brands = expertBrandRepository.findByEngineer_Id(profile.getUser().getId()).stream()
                            .map(EngineerExpertBrand::getBrandName)
                            .toList();
                    return CustomerEngineerSummaryResponse.from(profile, brands);
                })
                .toList();
    }

    /**
     * 선택한 기사의 가능 일정(날짜 -> 시간대 목록) 조회
     */
    public CustomerEngineerAvailabilityResponse getEngineerAvailability(Long engineerId, LocalDate from, LocalDate to) {
        engineerProfileRepository.findByUser_Id(engineerId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 기사입니다."));

        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(27);

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("조회 시작일이 종료일보다 늦을 수 없습니다.");
        }

        List<EngineerSchedule> schedules = engineerScheduleRepository
                .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerId, start, end);

        Map<String, List<String>> availableDates = new LinkedHashMap<>();
        for (EngineerSchedule schedule : schedules) {
            if (schedule.getStatus() != ScheduleStatus.AVAILABLE) {
                continue;
            }
            List<String> times = schedule.getTimeSlots().stream()
                    .map(slot -> slot.getStartTime().toString().substring(0, 5))
                    .toList();
            availableDates.put(schedule.getWorkDate().toString(), times);
        }

        return CustomerEngineerAvailabilityResponse.builder()
                .engineerId(engineerId)
                .availableDates(availableDates)
                .build();
    }
}
