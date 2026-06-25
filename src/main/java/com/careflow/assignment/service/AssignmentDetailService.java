package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentDetailResponse;
import com.careflow.assignment.dto.AssignmentDetailResponse.EngineerProfileInfo;
import com.careflow.assignment.dto.AssignmentDetailResponse.StatusLogInfo;
import com.careflow.assignment.dto.AssignmentDetailResponse.WorkReportInfo;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.assignment.repository.AsStatusLogRepository;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.report.repository.WorkReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AssignmentDetailService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final ExpectedRepairCostRepository expectedRepairCostRepository;
    private final WorkReportRepository workReportRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    /**
     * 배차 내역 단건 상세 조회.
     * 1. 권한 확인 — role != AGENCY 이면 401
     * 2. assignmentId 로 AsAssignment 조회 (없으면 404)
     * 3. AsAssignment → AsRequest → Symptom/Customer/Appliance 연쇄 추출
     * 4. symptom_id 로 예상 수리 비용 조회 (미집계 시 null 허용)
     * 5. request_id 로 처리 이력(as_status_logs) 조회
     * 6. appliance_id 로 동일 가전의 이전 수리 이력(work_reports) 조회
     * 7. engineer_id 로 기사 프로필(engineer_profiles) 조회 (미등록 시 null 허용)
     */
    @Transactional(readOnly = true)
    public AssignmentDetailResponse getDetail(Long assignmentId,
                                              CustomUserDetails userDetails) throws IllegalAccessException {
        // 1. 권한 검증
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        // 2. 배차 단건 조회 — as_requests·symptom·customer·appliance·engineer 를 JOIN FETCH 로 한 번에 로딩
        AsAssignment assignment = asAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("해당 배차 내역을 찾을 수 없습니다."));

        var asRequest = assignment.getAsRequest();
        Long requestId    = asRequest.getId();
        Long symptomId    = asRequest.getSymptom().getId();
        Long applianceId  = asRequest.getAppliance().getId();
        Long engineerId   = assignment.getEngineer().getId();

        // 3. 예상 수리 비용 — Quartz 집계 전 데이터 없을 수 있음
        Integer avgRepairCost = expectedRepairCostRepository
                .findBySymptom_Id(symptomId)
                .map(cost -> cost.getAvgCost())
                .orElse(null);

        // 4. 처리 이력 (시간 오름차순)
        List<StatusLogInfo> statusLogs = asStatusLogRepository
                .findByAsRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(StatusLogInfo::from)
                .toList();

        // 5. 동일 가전제품에 대한 이전 수리 이력
        List<WorkReportInfo> previousWorkReports = workReportRepository
                .findByAsRequest_Appliance_Id(applianceId)
                .stream()
                .map(WorkReportInfo::from)
                .toList();

        // 6. 배정 기사 프로필 (미등록 시 null)
        EngineerProfileInfo engineerProfile = engineerProfileRepository
                .findByUser_Id(engineerId)
                .map(EngineerProfileInfo::from)
                .orElse(null);

        return AssignmentDetailResponse.of(
                assignment, avgRepairCost, statusLogs, previousWorkReports, engineerProfile);
    }
}
