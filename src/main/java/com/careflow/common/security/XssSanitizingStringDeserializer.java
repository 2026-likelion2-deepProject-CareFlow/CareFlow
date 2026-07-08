package com.careflow.common.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.IOException;

/**
 * JSON 요청 바디(@RequestBody)로 들어오는 모든 문자열 필드를 대상으로 하는 전역 XSS 방어 디시리얼라이저.
 * DTO/컨트롤러를 개별 수정하지 않고 Jackson ObjectMapper 레벨(JacksonXssConfig)에서 String.class에 등록되어
 * record/Lombok DTO, 중첩 객체, 리스트 등 모든 문자열 입력 지점에 자동 적용된다.
 *
 * 정규식 블랙리스트 대신 Jsoup의 실제 HTML 파서로 유효성을 검사한다. Safelist.none()은 태그를 전혀 허용하지
 * 않으므로, 값에 "<"로 시작하는 HTML 태그/코멘트 형태(<script>, <img onerror=..>, <svg>, 대소문자/중첩 변형,
 * 완성되지 않은 태그 등)가 하나라도 파싱되면 즉시 요청을 거부한다. 이 프로젝트의 필드(이름/주소/메모 등)에는
 * "<" 문자 자체가 정상적으로 등장할 일이 거의 없으므로, "<"가 포함된 입력은 보수적으로 전부 차단한다.
 */
public class XssSanitizingStringDeserializer extends StringDeserializer {

    private static final String VIOLATION_MESSAGE = "입력값에 허용되지 않는 스크립트/HTML 태그가 포함되어 있습니다.";

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = super.deserialize(p, ctxt);
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (!Jsoup.isValid(value, Safelist.none())) {
            throw new IllegalArgumentException(VIOLATION_MESSAGE);
        }

        return value;
    }
}
