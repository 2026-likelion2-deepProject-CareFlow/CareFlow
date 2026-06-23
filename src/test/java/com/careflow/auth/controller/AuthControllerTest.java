package com.careflow.auth.controller;

import com.careflow.auth.dto.SignUpRequest;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.service.AuthService;
import com.careflow.common.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import  static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private ObjectMapper objectMapper;

    private SignUpRequest createSignUpRequest(){
        final String email = "ghwns6659@gmail.com";
        final String password = "12345678";
        final String name = "서호준";
        final String phoneNumber = "010-1234-5678";
        final String regionName = "부산 광역시 사상구";
        final String addressDetail = "부산 광역시 사상구";

        return new SignUpRequest(email, password, name, phoneNumber, regionName, addressDetail);
    }

    @Test
    void signUp() throws Exception {
        SignUpRequest signUpRequest = createSignUpRequest();
        final String requestJson = objectMapper.writeValueAsString(signUpRequest);

        String expectedMessage = "회원가입이 완료되었습니다.";
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void login() {
    }

    @Test
    void refresh() {
    }
}