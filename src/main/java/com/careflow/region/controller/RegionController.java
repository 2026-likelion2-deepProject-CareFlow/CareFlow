package com.careflow.region.controller;

import com.careflow.region.dto.RegionRequest;
import com.careflow.region.entity.Regions;
import com.careflow.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ResponseEntity<Regions> getRegion(@RequestBody RegionRequest request) {

        Regions regions = regionService.findByName(request.name());
        return ResponseEntity.ok(regions);
    }
}
