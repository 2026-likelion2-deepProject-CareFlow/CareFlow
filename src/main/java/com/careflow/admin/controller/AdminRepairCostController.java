package com.careflow.admin.controller;

import com.careflow.assignment.entity.ExpectedRepairCost;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/repair-costs")
@RequiredArgsConstructor
public class AdminRepairCostController {

    private final ExpectedRepairCostRepository expectedRepairCostRepository;

    // 1. 수리 비용 가이드 조회
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<RepairCostDto>> getRepairCosts(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {
        checkAdminRole(userDetails);

        List<RepairCostDto> list = expectedRepairCostRepository.findAllWithCategoryAndSymptom()
                .stream()
                .map(cost -> new RepairCostDto(
                        cost.getId(),
                        cost.getCategory().getCategoryId(),
                        cost.getCategory().getName(),
                        cost.getSymptom().getSymptomName(),
                        cost.getAvgCost() != null ? cost.getAvgCost() : 0
                )).toList();

        return ResponseEntity.ok(list);
    }

    // 2. 수리 비용 가이드 수정
    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<RepairCostDto> updateRepairCost(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {
        checkAdminRole(userDetails);

        ExpectedRepairCost cost = expectedRepairCostRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 수리 비용 데이터입니다."));

        cost.updateAvgCost(request.get("avgCost"));

        return ResponseEntity.ok(new RepairCostDto(
                cost.getId(), cost.getCategory().getCategoryId(),
                cost.getCategory().getName(), cost.getSymptom().getSymptomName(), cost.getAvgCost()
        ));
    }

    private void checkAdminRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"ADMIN".equals(userDetails.getRole())) throw new IllegalAccessException("관리자만 접근 가능합니다.");
    }

    // DTO 레코드 내부 정의
    public record RepairCostDto(Long id, Integer categoryId, String categoryName, String symptom, Integer avgCost) {}
}