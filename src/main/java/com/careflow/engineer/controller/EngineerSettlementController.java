package com.careflow.engineer.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.EngineerSettlementPageResponse;
import com.careflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ENGINEER')") // 권한 설정 확실하게!
public class EngineerSettlementController {

    private final SettlementRepository settlementRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<EngineerSettlementPageResponse>> getSettlements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<EngineerSettlementPageResponse> response = settlementRepository
                .findPageByEngineerId(userDetails.getUserId(), pageRequest)
                .map(EngineerSettlementPageResponse::from);

        return ResponseEntity.ok(response);
    }
}