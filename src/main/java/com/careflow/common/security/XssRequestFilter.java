package com.careflow.common.security;

import com.careflow.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 쿼리 파라미터 등 JSON 바디를 거치지 않는 입력에 대한 XSS 방어 필터.
 * JSON 바디(@RequestBody)는 XssSanitizingStringDeserializer(Jackson)가 별도로 처리하므로,
 * 여기서는 request.getParameterMap()(쿼리스트링/폼 파라미터)만 검사한다.
 * JwtFilter보다 앞단에 배치되어 인증 여부와 무관하게 모든 요청(permitAll 경로 포함)에 적용된다.
 */
@Component
@RequiredArgsConstructor
public class XssRequestFilter extends OncePerRequestFilter {

    private static final String VIOLATION_MESSAGE = "입력값에 허용되지 않는 스크립트/HTML 태그가 포함되어 있습니다.";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            for (String value : entry.getValue()) {
                if (value != null && !value.isEmpty() && !Jsoup.isValid(value, Safelist.none())) {
                    writeRejection(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeRejection(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(VIOLATION_MESSAGE)));
    }
}
