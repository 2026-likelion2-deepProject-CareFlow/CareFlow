package com.careflow.assignment.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.dto.EngineerAssignmentResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngineerAssignmentService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final ExpectedRepairCostRepository expectedRepairCostRepository;

    @Transactional
    public void acceptAssignment(Long engineerId, Long assignmentId) {
        AsAssignment assignment = getAssignmentAndValidateOwnership(engineerId, assignmentId);
        assignment.accept();
        if (assignment.getAsRequest() != null) {
            assignment.getAsRequest().acceptAssignment();
        }
    }

    @Transactional
    public void rejectAssignment(Long engineerId, Long assignmentId, AssignmentRejectRequest request) {
        AsAssignment assignment = getAssignmentAndValidateOwnership(engineerId, assignmentId);
        assignment.reject(request.rejectReason());
        if (assignment.getAsRequest() != null) {
            assignment.getAsRequest().revertToAgencyReceived();
        }
    }

    @Transactional(readOnly = true)
    public Page<EngineerAssignmentResponse> getAssignments(Long engineerId, String status, Pageable pageable) {
        String filterStatus = "ALL".equalsIgnoreCase(status) ? null : status;
        Page<AsAssignment> assignments = asAssignmentRepository.findByEngineerIdAndStatus(engineerId, filterStatus, pageable);

        // 🌟 방어 로직 1: AsRequest가 null이 아닌 것만 ID 추출
        List<Long> requestIds = assignments.getContent().stream()
                .filter(a -> a.getAsRequest() != null)
                .map(a -> a.getAsRequest().getId())
                .toList();

        // 🌟 방어 로직 2: AsRequest와 Symptom이 null이 아닌 것만 ID 추출
        List<Long> symptomIds = assignments.getContent().stream()
                .filter(a -> a.getAsRequest() != null && a.getAsRequest().getSymptom() != null)
                .map(a -> a.getAsRequest().getSymptom().getId())
                .distinct()
                .toList();

        Map<Long, String> latestLogMap = asStatusLogRepository.findAll().stream()
                .filter(log -> log.getAsRequest() != null && requestIds.contains(log.getAsRequest().getId()))
                .collect(Collectors.groupingBy(
                        log -> log.getAsRequest().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy((l1, l2) -> {
                                    if (l1.getCreatedAt() == null) return -1;
                                    if (l2.getCreatedAt() == null) return 1;
                                    return l1.getCreatedAt().compareTo(l2.getCreatedAt());
                                }),
                                opt -> opt.map(AsStatusLog::getToStatus).orElse("WAITING")
                        )
                ));

        Map<Long, Integer> expectedCostMap = expectedRepairCostRepository.findAll().stream()
                .filter(cost -> cost.getSymptom() != null && symptomIds.contains(cost.getSymptom().getId()))
                .collect(Collectors.toMap(
                        cost -> cost.getSymptom().getId(),
                        cost -> cost.getAvgCost() != null ? cost.getAvgCost() : 0,
                        (existing, replacement) -> existing
                ));

        return assignments.map(a -> {
            Long reqId = a.getAsRequest() != null ? a.getAsRequest().getId() : null;
            Long sympId = (a.getAsRequest() != null && a.getAsRequest().getSymptom() != null) ? a.getAsRequest().getSymptom().getId() : null;

            String latestLogStatus = reqId != null ? latestLogMap.get(reqId) : "WAITING";
            Integer avgCost = sympId != null ? expectedCostMap.get(sympId) : 0;

            return EngineerAssignmentResponse.of(a, latestLogStatus, avgCost);
        });
    }

    private AsAssignment getAssignmentAndValidateOwnership(Long engineerId, Long assignmentId) {
        AsAssignment assignment = asAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배정 내역입니다."));

        if (assignment.getEngineer() == null || !assignment.getEngineer().getId().equals(engineerId)) {
            throw new IllegalStateException("본인에게 배정된 건만 처리할 수 있습니다.");
        }
        return assignment;
    }
}