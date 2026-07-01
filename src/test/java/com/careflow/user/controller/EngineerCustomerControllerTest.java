package com.careflow.user.controller;

import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerCustomerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerCustomerController 단위 테스트")
class EngineerCustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AsAssignmentRepository asAssignmentRepository;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor engineerAuth;

    @BeforeEach
    void setUp() {
        engineerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(1L, "engineer@test.com", "pw", "ENGINEER", 100L),
                        null, List.of(new SimpleGrantedAuthority("ENGINEER"))
                )
        );
    }

    @Test
    @DisplayName("성공: 페이징을 통해 고객 목록이 정상적으로 조회된다.")
    void getMyCustomers_Success() throws Exception {
        given(asAssignmentRepository.findCustomersByEngineerId(eq(1L), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/engineer/customers").with(engineerAuth))
                .andExpect(status().isOk());
    }
}