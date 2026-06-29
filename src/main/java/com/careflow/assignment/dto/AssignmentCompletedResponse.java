package com.careflow.assignment.dto;

import java.time.LocalDateTime;

public record AssignmentCompletedResponse(
        Long assignmentId,
        Long requestId,

        Long customerId,
        String customerName,
        String customerPhone,

        String productName,
        String modelNo,

        Long engineerId,
        String engineerName,
        String engineerPhone,
        Double engineerRating,
        String engineerImg,

        // work_reports 필드
        LocalDateTime submittedAt,
        Integer workDurationMin,
        String diagnosisResult,
        Integer finalAmount,
        String memo,

        String visitDate,
        String visitTime,
        String visitAddress,

        // reviews 필드 (없으면 null)
        Integer rating,
        String reviewContent,
        LocalDateTime reviewAt
) {}
