package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.dto.request.AgencyFeeRateUpdateRequest;
import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyFeeRateResponse;
import com.careflow.agency.dto.response.AgencyProfileResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
public class AgenciesController {

    private final AgenciesService agenciesService;

    // 대행사 회원가입 API
    // 대행사 회원가입 시도 시 사전에 조회된 대행사 정보가 있는지 없는지 여부 판별
    // 대행사 정보 존재 시 AgencyCreateRequest.flag = 1, 정보 없을 시 AgencyCreateRequest.flag = 0
    @PostMapping("/signup")
    public ResponseEntity<Long> signupAgency(@Valid @RequestBody AgencyCreateRequest agencyCreateRequest) {

        // 대행사 정보가 존재할 경우 - 사용자가 대행사 정보 직접 입력 x, 대행사 정보 조회 응답 데이터 그대로 활용
        // 슈퍼계정 생성 요청 및 대행사 정보 저장
        Long accountRequestId = agenciesService.requestAgencyAccount(agencyCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountRequestId);

    }

    /*
    대행사 회원가입 절차 시 대행사 이름, 사업자 등록번호를 통해 기존에 저장된 데이터가 있는지 검색
    기존에 데이터가 있는 경우 데이터 반환 및 대행사 회원가입 -> 관리자 계정요청으로 서비스 넘어감
     */
    @GetMapping("/agency")
    public ResponseEntity<Agencies> getAgency(@Valid @RequestParam(name = "agencyName") String agencyName) {

        // 대행사 계정 회원가입 시도 중 대행사 조회 결과 200 ok 반환될 경우 프론트에서 flag = 1 설정
        // 대행사 조회 결과가 존재하지 않아 NoSuchElementException 발생 및 NotFound 반환 시 flag = 0 설정
        Agencies agencies = agenciesService.findByAgencyName(agencyName);
        return ResponseEntity.ok(agencies);
    }

    // ─────────────────────────────────────────────
    //  대행사 설정 API (인증 필요 — ROLE_AGENCY)
    // ─────────────────────────────────────────────

    // 대행사 프로필(상호명, 주소) 수정
    // JWT에서 userId 추출 → 해당 대행사 조회 → 상호명/주소 갱신
    @PatchMapping("/profile")
    public ResponseEntity<AgencyProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgencyProfileUpdateRequest request) {

        AgencyProfileResponse response = agenciesService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    // 대행사 수수료율 조회
    @GetMapping("/fee-rate")
    public ResponseEntity<AgencyFeeRateResponse> getFeeRate(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AgencyFeeRateResponse response = agenciesService.getFeeRate(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    // 대행사 수수료율 수정
    // 변경된 수수료율은 이후 생성되는 정산(settlements)에 적용됨 — 기존 정산 소급 적용 없음
    @PatchMapping("/fee-rate")
    public ResponseEntity<AgencyFeeRateResponse> updateFeeRate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgencyFeeRateUpdateRequest request) {

        AgencyFeeRateResponse response = agenciesService.updateFeeRate(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }
}
