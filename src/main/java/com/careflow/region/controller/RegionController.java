package com.careflow.region.controller;

import com.careflow.region.dto.RegionRequest;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;
    private final RegionRepository regionRepository;

    @GetMapping
    public ResponseEntity<List<Regions>> getRegions(
            @RequestParam(required = false) Integer depth,
            @RequestParam(required = false) String name) {

        // depth가 들어오면 해당 depth의 지역 목록을 반환 (프론트 드롭다운용)
        if (depth != null) {
            return ResponseEntity.ok(regionRepository.findByDepth(depth));
        }
        // name이 들어오면 기존처럼 단건 반환
        if (name != null) {
            return ResponseEntity.ok(List.of(regionService.findByName(name)));
        }
        return ResponseEntity.ok(regionRepository.findAll());
    }
}
