package com.careflow.part.controller;

import com.careflow.part.dto.RepairPartResponse;
import com.careflow.part.repository.RepairPartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/engineer/repair-parts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')") //  기사 권한 보호 추가
public class RepairPartController {

    private final RepairPartRepository repairPartRepository;

    @GetMapping
    public ResponseEntity<List<RepairPartResponse>> searchParts(
            @RequestParam(name = "search", required = false) String search) { // 파라미터명 'search'로 매핑

        // 빈 문자열 방어 로직
        String effectiveSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        // findAll() 인메모리 필터링 제거 및 최적화된 DB 쿼리 호출
        List<RepairPartResponse> parts = repairPartRepository.searchByKeyword(effectiveSearch).stream()
                .map(RepairPartResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(parts);
    }
}