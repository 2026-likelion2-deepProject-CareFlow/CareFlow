package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentCompletedResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentCompletedService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final WorkReportRepository workReportRepository;
    private final ReviewRepository reviewRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    @Transactional(readOnly = true)
    public List<AssignmentCompletedResponse> getCompleted(CustomUserDetails userDetails)
            throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        // COMPLETED 상태 배정 목록 조회
        List<AsAssignment> assignments = asAssignmentRepository.findCompletedByAgencyId(agencyId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        // 기사 프로필 배치 조회
        List<Long> engineerIds = assignments.stream()
                .map(a -> a.getEngineer().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, EngineerProfile> profileMap = engineerProfileRepository
                .findByUser_IdIn(engineerIds)
                .stream()
                .collect(Collectors.toMap(ep -> ep.getUser().getId(), ep -> ep));

        // 작업 보고서 배치 조회
        List<Long> requestIds = assignments.stream()
                .map(a -> a.getAsRequest().getId())
                .collect(Collectors.toList());
        Map<Long, WorkReport> reportMap = workReportRepository
                .findByAsRequestIdIn(requestIds)
                .stream()
                .collect(Collectors.toMap(w -> w.getAsRequest().getId(), w -> w));

        // 리뷰 배치 조회 (없는 건은 null 처리)
        Map<Long, Review> reviewMap = reviewRepository
                .findByAsRequestIdIn(requestIds)
                .stream()
                .collect(Collectors.toMap(r -> r.getAsRequest().getId(), r -> r));

        return assignments.stream()
                .filter(a -> reportMap.containsKey(a.getAsRequest().getId()))  // 보고서 없는 건 제외
                .map(a -> toResponse(a, profileMap, reportMap, reviewMap))
                .collect(Collectors.toList());
    }

    private AssignmentCompletedResponse toResponse(
            AsAssignment a,
            Map<Long, EngineerProfile> profileMap,
            Map<Long, WorkReport> reportMap,
            Map<Long, Review> reviewMap) {

        var req = a.getAsRequest();
        var engineer = a.getEngineer();
        var appliance = req.getAppliance();
        EngineerProfile profile = profileMap.get(engineer.getId());
        WorkReport report = reportMap.get(req.getId());
        Review review = reviewMap.get(req.getId());

        return new AssignmentCompletedResponse(
                a.getId(),
                req.getId(),

                req.getCustomer().getId(),
                req.getCustomer().getName(),
                req.getCustomer().getPhone(),

                appliance.getBrand() + " " + appliance.getModelName(),
                appliance.getModelName(),

                engineer.getId(),
                engineer.getName(),
                engineer.getPhone(),
                profile != null ? profile.getAvgRating().doubleValue() : null,
                profile != null ? profile.getProfileImageUrl() : null,

                report.getSubmittedAt(),
                report.getWorkDurationMin(),
                report.getDiagnosisResult().name(),
                report.getFinalAmount(),
                report.getMemo(),

                req.getScheduledDate().toString(),
                req.getScheduledTime(),
                req.getVisitAddressDetail(),

                review != null ? review.getRating() : null,
                review != null ? review.getContent() : null,
                review != null ? review.getCreatedAt() : null
        );
    }
}
