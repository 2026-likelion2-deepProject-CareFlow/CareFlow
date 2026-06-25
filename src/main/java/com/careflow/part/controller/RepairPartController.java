package com.careflow.part.controller;

import com.careflow.part.dto.RepairPartResponse;
import com.careflow.part.repository.RepairPartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repair-parts")
@RequiredArgsConstructor
public class RepairPartController {

    private final RepairPartRepository repairPartRepository;

    @GetMapping
    public ResponseEntity<List<RepairPartResponse>> searchParts(@RequestParam(required = false) String keyword) {
        List<RepairPartResponse> parts = repairPartRepository.findAll().stream()
                .filter(part -> keyword == null ||
                        part.getPartName().contains(keyword) ||
                        part.getPartCode().contains(keyword))
                .map(RepairPartResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(parts);
    }
}