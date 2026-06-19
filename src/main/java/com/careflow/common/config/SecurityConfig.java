package com.careflow.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API이므로 CSRF 보호 비활성화 (세션 기반 인증을 안 쓴다면 보통 비활성화)
                .csrf(csrf -> csrf.disable())

                // 경로별 인가 규칙 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/agency/signup").permitAll()
                        .anyRequest().authenticated() // 나머지 경로는 인증 필요 (필요에 맞게 조정)
                );

        return http.build();
    }
}
