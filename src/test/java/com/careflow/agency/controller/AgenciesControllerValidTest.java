package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;

import  static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(AgenciesController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
class AgenciesControllerValidTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgenciesService agenciesService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    // develop 병합으로 추가된 OAuth2 의존성 — SecurityConfig 생성 및 oauth2Login() 설정에 필요
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private ObjectMapper objectMapper;


    // 유효성 검증 실패 - 사용자 이름은 비어있는 값이 들어올 수 없음
    @Test
    void signupAgencyUserName() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 이메일은 비어있는 값, 또는 이메일의 형식을 따르지 않는 데이터가 들어올 수 없음
    @Test
    void signupAgencyEmail() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 휴대폰 번호는 휴대폰 번호의 형식에 맞는 데이터가 입력되어야 함
    @Test
    void signupAgencyPhoneNumber() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 상호명은 비어있는 값이 들어올 수 없음
    @Test
    void signupAgencyBlankAgencyName() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 사업자 등록번호는 비어있는 값이 들어올 수 없음
    @Test
    void signupAgencyBlankBusinessName() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 대행사 주소지 정보는 255글자를 넘을 수 없다.
    @Test
    void signupAgencyAgencyAddress() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 대행사 수수료율 정보는 null 값이 들어올 수 없다.
    @Test
    void signupAgencyAgencyFeeRateNULL() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }

    // 유효성 검증 실패 - 대행사 수수료율 데이터는 소수점 2자리를 초과할 수 없다.
    @Test
    void signupAgencyAgencyFeeRateFraction() throws Exception {
        // given
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

        // 레코드 요청 객체 생성
        final AgencyCreateRequest agencyCreateRequest = new AgencyCreateRequest(name, email, password, phoneNumber, regionsName, addressDetail ,agencyName, businessNumber, agencyAddress, agencyFeeRate);

        // 객체를 JSON 문자열로 변환
        final String requestJson = objectMapper.writeValueAsString(agencyCreateRequest);
        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        // 실제로 던져진 예외가 MethodArgumentNotValidException 인지 검증
                        assertInstanceOf(
                                MethodArgumentNotValidException.class,
                                result.getResolvedException()
                        )
                );
    }
}