package com.careflow.common.config;

import com.careflow.common.security.XssSanitizingStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot가 자동 구성하는 ObjectMapper(모든 @RequestBody JSON 역직렬화에 사용됨)에
 * XssSanitizingStringDeserializer를 String.class 전역 디시리얼라이저로 등록한다.
 * 이렇게 하면 개별 DTO 필드마다 검증 로직을 추가하지 않아도 모든 API의 문자열 입력이 자동으로 검사된다.
 */
@Configuration
public class JacksonXssConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer xssSanitizingJacksonCustomizer() {
        SimpleModule xssModule = new SimpleModule("XssSanitizingModule");
        xssModule.addDeserializer(String.class, new XssSanitizingStringDeserializer());
        return builder -> builder.modulesToInstall(xssModule);
    }
}
