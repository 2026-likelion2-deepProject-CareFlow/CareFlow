package com.careflow.common.config;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.JwtFilter;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.security.XssRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final XssRequestFilter xssRequestFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    // 1. [핵심] H2 콘솔을 시큐리티 필터(JwtFilter 포함) 거치지 않게 완전히 제외
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                // h2 콘솔 iframe 랜더링을 위한 헤더 설정 추가
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        // AccessDeniedHandler/AuthenticationEntryPoint가 response.sendError()로 위임하면
                        // 컨테이너가 /error로 내부 forward하는데, 이때는 ERROR dispatch라 JwtFilter가
                        // 재실행되지 않아 SecurityContext가 익명으로 바뀜 — /error를 막아두면 원래
                        // 의도했던 상태코드(403 등)가 401로 뒤바뀌어 나가므로 permitAll 필요
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/agencies/signup").permitAll()
                        .requestMatchers("/api/agencies/agency").permitAll()
                        .requestMatchers("/api/engineer/signup").permitAll()
                        .requestMatchers("/api/auth/logout").authenticated() // 로그아웃은 인증 필수
                        .requestMatchers("/api/auth/password").authenticated() // 로그인 상태 비밀번호 변경(PUT)은 인증 필수 — /password/send-code 등 하위 경로와는 별개의 정확한 경로
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 대행사 설정 — AGENCY 역할만 접근 가능
                        // CustomUserDetails.getAuthorities()가 "ROLE_" 접두사를 붙이므로 hasRole 사용(hasAuthority 아님)
                        .requestMatchers("/api/agencies/profile").hasRole("AGENCY")
                        .requestMatchers("/api/agencies/fee-rate").hasRole("AGENCY")
                        .requestMatchers("/api/regions/**").permitAll()
                        // 대행사 프로필 수정 (리팩토링된 경로)
                        .requestMatchers("/api/agency/me").hasRole("AGENCY")
                        // 관리자용 회원 관리 API — ADMIN 역할만 접근 가능(컨트롤러의 checkAdminRole과 이중 방어)
                        // CustomUserDetails.getAuthorities()가 "ROLE_" 접두사를 붙이므로 hasRole 사용(hasAuthority 아님)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                // Stateless REST API — 미인증 요청에 대해 OAuth2 로그인 리다이렉트(302) 대신 401 반환
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .addFilterBefore(xssRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}