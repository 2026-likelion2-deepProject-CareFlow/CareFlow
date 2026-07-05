package com.careflow.region.controller;

import com.careflow.region.dto.RegionResponse;
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

    @GetMapping
    public ResponseEntity<List<RegionResponse>> getRegions(
            @RequestParam(required = false) Integer depth,
            @RequestParam(required = false) String name) {

        // depth가 들어오면 해당 depth의 지역 목록을 DTO 로 반환 (프론트 드롭다운용)
        if (depth != null) {
            return ResponseEntity.ok(regionService.getRegionsByDepth(depth));
        }
        // name이 들어오면 단건을 DTO 로 반환
        if (name != null) {
            return ResponseEntity.ok(List.of(regionService.getRegionResponseByName(name)));
        }
        return ResponseEntity.ok(regionService.getAllRegions());
    }
}
