package com.careflow.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// PasswordEncoder 를 SecurityConfig 에서 분리
// — SecurityConfig 가 CustomOAuth2UserService → AuthService → PasswordEncoder 를 간접 의존하므로
//   같은 클래스에 빈 정의를 두면 순환 참조가 발생함
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
