package com.careflow.common.config;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.JwtFilter;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    // 1. [핵심] H2 콘솔을 시큐리티 필터(JwtFilter 포함) 거치지 않게 완전히 제외
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // h2 콘솔 iframe 랜더링을 위한 헤더 설정 추가
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/agencies/signup").permitAll()
                        .requestMatchers("/api/agencies/agency").permitAll()
                        .requestMatchers("/api/engineers/signup").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 대행사 설정 — AGENCY 역할만 접근 가능
                        // JWT payload의 role 클레임이 "AGENCY" 문자열로 저장되므로 ROLE_ 접두사 없이 매칭
                        .requestMatchers("/api/agencies/profile").hasAuthority("AGENCY")
                        .requestMatchers("/api/agencies/fee-rate").hasAuthority("AGENCY")
                        // 대행사 프로필 수정 (리팩토링된 경로)
                        .requestMatchers("/api/agency/me").hasAuthority("AGENCY")
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
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}