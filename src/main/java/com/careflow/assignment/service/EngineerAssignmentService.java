package com.careflow.assignment.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.dto.EngineerAssignmentResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.entity.ExpectedRepairCost;
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

        // 도메인 메서드 호출 (더티 체킹)
        assignment.accept();
        assignment.getAsRequest().acceptAssignment(); // A/S 요청 본체도 ACCEPTED 상태로 변경
    }

    @Transactional
    public void rejectAssignment(Long engineerId, Long assignmentId, AssignmentRejectRequest request) {
        AsAssignment assignment = getAssignmentAndValidateOwnership(engineerId, assignmentId);

        // 도메인 메서드 호출 (더티 체킹)
        assignment.reject(request.rejectReason());
        assignment.getAsRequest().revertToAgencyReceived(); // 대행사 재배정 대기로 원복
    }

    @Transactional(readOnly = true)
    public Page<EngineerAssignmentResponse> getAssignments(Long engineerId, String status, Pageable pageable) {
        // "ALL" 상태 필터링 대응
        String filterStatus = "ALL".equalsIgnoreCase(status) ? null : status;

        Page<AsAssignment> assignments = asAssignmentRepository.findByEngineerIdAndStatus(engineerId, filterStatus, pageable);

        // N+1 문제 방지를 위해 필요한 부가 데이터(상태 로그, 예상 비용)를 IN 쿼리로 한 번에 조회
        List<Long> requestIds = assignments.getContent().stream()
                .map(a -> a.getAsRequest().getId())
                .toList();

        List<Long> symptomIds = assignments.getContent().stream()
                .map(a -> a.getAsRequest().getSymptom().getId())
                .distinct()
                .toList();

        // 최신 상태 로그 맵 구성
        Map<Long, String> latestLogMap = asStatusLogRepository.findAll().stream() // 실무에선 findAll 대신 in 쿼리 권장
                .filter(log -> requestIds.contains(log.getAsRequest().getId()))
                .collect(Collectors.groupingBy(
                        log -> log.getAsRequest().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy((l1, l2) -> l1.getCreatedAt().compareTo(l2.getCreatedAt())),
                                opt -> opt.map(AsStatusLog::getToStatus).orElse("WAITING")
                        )
                ));

        // 예상 수리 비용 맵 구성
        Map<Long, Integer> expectedCostMap = expectedRepairCostRepository.findAll().stream()
                .filter(cost -> symptomIds.contains(cost.getSymptom().getId()))
                .collect(Collectors.toMap(
                        cost -> cost.getSymptom().getId(),
                        cost -> cost.getAvgCost() != null ? cost.getAvgCost() : 0,
                        (existing, replacement) -> existing
                ));

        return assignments.map(a -> {
            Long reqId = a.getAsRequest().getId();
            Long sympId = a.getAsRequest().getSymptom().getId();
            return EngineerAssignmentResponse.of(a, latestLogMap.get(reqId), expectedCostMap.get(sympId));
        });
    }

    private AsAssignment getAssignmentAndValidateOwnership(Long engineerId, Long assignmentId) {
        AsAssignment assignment = asAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배정 내역입니다."));

        if (!assignment.getEngineer().getId().equals(engineerId)) {
            throw new IllegalStateException("본인에게 배정된 건만 처리할 수 있습니다.");
        }
        return assignment;
    }
}