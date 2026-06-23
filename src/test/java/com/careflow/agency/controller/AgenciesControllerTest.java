package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.config.SecurityConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(AgenciesController.class)
@Import(SecurityConfig.class)
class AgenciesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgenciesService agenciesService;
    @MockitoBean
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private AgencyCreateRequest createAgencyRequest(){
        final String name = "";
        final String email = "ghwns6659@gmail.com";
        final String password = "123456";
        final String phoneNumber = "010-1234-5678";
        final String regionsName = "서울 특별시 강남구"; // 사용자 거주지역 기본키 값
        final String addressDetail = "서울 특별시 강남구"; // 사용자 상세 거주지역
        final String agencyName = ""; // 상호명 비어있을 때 테스트
        final String businessNumber = "123-45-67890"; // 사업자 등록번호는 보통 10자리 숫자로 구성
        final String agencyAddress = "서울 특별시 강남구";// 대행사 주소
        final Double agencyFeeRate = 5.2;

        return new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);
    }

    // 아직 승인 대기중이거나 승인거부된 대행사는 조회되어선 안된다.
    @Test
    void getAgency() {


    }
    // 요청 정상 수행여부는 로그인 필요 X
    @Test
    void signupAgencySuperAccount() throws JsonProcessingException {
        AgencyCreateRequest agencyCreateRequest = createAgencyRequest();
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
    }

    @Test
    void signupAgencyManagerAccount() {
    }


}