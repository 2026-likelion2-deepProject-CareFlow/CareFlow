package com.careflow.agency.dto.response;

import com.careflow.payment.entity.Payment;

import java.time.LocalDateTime;

// GET /api/agency/customers/{userId}/payments 응답 DTO
public record AgencyCustomerPaymentResponse(
        Long paymentId,
        Long requestId,
        String applianceBrand,
        String applianceModelName,
        Integer amount,
        String pgProvider,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static AgencyCustomerPaymentResponse from(Payment payment) {
        return new AgencyCustomerPaymentResponse(
                payment.getId(),
                payment.getAsRequest().getId(),
                payment.getAsRequest().getAppliance().getBrand(),
                payment.getAsRequest().getAppliance().getModelName(),
                payment.getAmount(),
                payment.getPgProvider().name(),
                payment.getStatus().name(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
