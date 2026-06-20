package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agency")
@RequiredArgsConstructor
public class AgenciesController {

    private final AgenciesService agenciesService;

    // 대행사 회원가입 API
    // 대행사 회원가입 시도 시 사전에 조회된 대행사 정보가 있는지 없는지 여부 판별
    // 대행사 정보 존재 시 AgencyCreateRequest.flag = 1, 정보 없을 시 AgencyCreateRequest.flag = 0
    @PostMapping("/signup")
    public ResponseEntity<Long> signupAgency(@Valid @RequestBody AgencyCreateRequest agencyCreateRequest) {

        // 대행사 정보가 존재할 경우 - 사용자가 대행사 정보 직접 입력 x, 대행사 정보 조회 응답 데이터 그대로 활용
        if (agencyCreateRequest.flag()){
            // 관리자 계정 승인 요청
            Long accountRequestId = agenciesService.agencyManagerAccountRequest(agencyCreateRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(accountRequestId);

        } else{ // 대행사 정보가 존재하지 않을 경우
            // 슈퍼계정 생성 요청 및 대행사 정보 저장
            Long accountRequestId = agenciesService.agencySuperAccountRequest(agencyCreateRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(accountRequestId);
        }
    }

    /*
    대행사 회원가입 절차 시 대행사 이름, 사업자 등록번호를 통해 기존에 저장된 데이터가 있는지 검색
    기존에 데이터가 있는 경우 데이터 반환 및 대행사 회원가입 -> 관리자 계정요청으로 서비스 넘어감
     */
    @GetMapping("/getAgency")
    public ResponseEntity<Agencies> getAgency(@Valid @RequestParam(name = "name") String agencyName,
                                              @Valid @RequestParam(name = "businessNumber") String businessNumber) {

        // 대행사 계정 회원가입 시도 중 대행사 조회 결과 200 ok 반환될 경우 프론트에서 flag = 1 설정
        // 대행사 조회 결과가 존재하지 않아 NoSuchElementException 발생 및 NotFound 반환 시 flag = 0 설정
        Agencies agencies = agenciesService.findAgencyByNameAndNumber(agencyName, businessNumber);
        return ResponseEntity.ok(agencies);
    }

}
