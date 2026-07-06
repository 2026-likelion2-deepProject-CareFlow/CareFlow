package com.careflow.payment.client;

import com.careflow.payment.dto.TossConfirmResponse;
import com.careflow.payment.dto.TossErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인 API 서버-to-서버 연동
 * https://docs.tosspayments.com/reference#결제-승인
 */
@Slf4j
@Component
public class TossPaymentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String basicAuthHeader;

    public TossPaymentClient(@Value("${toss.secret-key}") String secretKey, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .build();
        this.objectMapper = objectMapper;
        // 토스 API는 시크릿 키 뒤에 콜론(:)을 붙여 Base64 인코딩한 값을 Basic 인증 헤더로 사용
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 결제 승인 요청 — 결제위젯에서 받은 paymentKey/orderId와 우리 서버가 신뢰하는 금액(amount)으로 확정한다.
     * 토스 쪽에 등록된 결제 금액과 여기서 보낸 amount가 다르면 토스가 자체적으로 승인을 거부한다.
     */
    public TossConfirmResponse confirm(String paymentKey, String orderId, int amount) {
        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Authorization", basicAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "paymentKey", paymentKey,
                            "orderId", orderId,
                            "amount", amount
                    ))
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("토스페이먼츠 결제 승인 실패 — status={}, body={}", e.getStatusCode(), body);
            throw new IllegalStateException("결제 승인에 실패했습니다. (사유: " + extractMessage(body) + ")");
        }
    }

    private String extractMessage(String errorBody) {
        try {
            TossErrorResponse error = objectMapper.readValue(errorBody, TossErrorResponse.class);
            return error.message() != null ? error.message() : errorBody;
        } catch (Exception parseError) {
            return errorBody;
        }
    }
}
