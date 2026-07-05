package com.careflow.region.service;

import com.careflow.region.dto.RegionResponse;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    public Regions findByName(String name){
        return regionRepository.findByName(name).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));
    }

    /** depth 별 지역 목록을 DTO 로 변환해 반환 (프론트 드롭다운용, sort_order 정렬) */
    @Transactional(readOnly = true)
    public List<RegionResponse> getRegionsByDepth(int depth) {
        return regionRepository.findByDepth(depth).stream()
                .sorted(Comparator.comparingInt(Regions::getSortOrder))
                .map(RegionResponse::from)
                .toList();
    }

    /** 전체 지역 목록을 DTO 로 변환해 반환 */
    @Transactional(readOnly = true)
    public List<RegionResponse> getAllRegions() {
        return regionRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Regions::getSortOrder))
                .map(RegionResponse::from)
                .toList();
    }

    /** 지역명으로 단건 조회 후 DTO 로 변환해 반환 */
    @Transactional(readOnly = true)
    public RegionResponse getRegionResponseByName(String name) {
        return RegionResponse.from(findByName(name));
    }
}
