package com.careflow.report.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.notification.service.NotificationService;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.dto.EngineerReportListResponse;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkReportService 단위 테스트 (Mock 기반 완전 분리)")
class WorkReportServiceTest {

    @InjectMocks private WorkReportService workReportService;

    @Mock private WorkReportRepository workReportRepository;
    @Mock private RepairPartRepository repairPartRepository;
    @Mock private HealthCertificateRepository healthCertificateRepository;
    @Mock private AsRequestRepository asRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private ApplianceRepository applianceRepository;
    @Mock private AsStatusLogRepository asStatusLogRepository;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("성공: 부품 교체 없음 -> 100점으로 진단서 갱신 및 SSE 이벤트 발행")
    void submitWorkReport_NoParts_Success() throws Exception {
        User engineer = mock(User.class);
        given(engineer.getName()).willReturn("김기사");

        AsRequest asRequest = mock(AsRequest.class);
        Appliance appliance = mock(Appliance.class);
        HealthCertificate certificate = mock(HealthCertificate.class);
        WorkReport savedReport = mock(WorkReport.class);
        User customer = mock(User.class);

        given(asRequest.getStatus()).willReturn(com.careflow.common.enums.AsStatus.IN_PROGRESS);
        given(asRequest.getAppliance()).willReturn(appliance);
        given(asRequest.getCustomer()).willReturn(customer);
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("세탁기");

        AsAssignment assignment = validAssignment();

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getId()).willReturn(100L);
        given(workReportRepository.existsByAsRequest_Id(100L)).willReturn(false);
        given(asAssignmentRepository.findByAsRequest_Id(100L)).willReturn(List.of(assignment));
        given(appliance.getId()).willReturn(200L);
        given(healthCertificateRepository.findByAppliance_Id(200L)).willReturn(Optional.of(certificate));
        given(savedReport.getReportId()).willReturn(999L);
        given(workReportRepository.save(any(WorkReport.class))).willReturn(savedReport);

        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);
        Long reportId = workReportService.submitWorkReport(1L, request);

        assertThat(reportId).isEqualTo(999L);
        verify(asRequest).completeWork();
        verify(certificate).calculateAndUpdateHealth(null, null);
        verify(asStatusLogRepository, times(1)).save(any());
        verify(eventPublisher, atLeastOnce()).publishEvent(any(com.careflow.notification.event.AsStatusNotificationEvent.class));
    }

    @Test
    @DisplayName("실패: 본인에게 배정된 건이 아님 (권한 없음)")
    void submitWorkReport_Fail_NotAssignedToMe() throws Exception {
        User engineer = mock(User.class);
        AsRequest asRequest = mock(AsRequest.class);
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        AsAssignment assignment = mock(AsAssignment.class);
        User otherEngineer = mock(User.class);
        given(assignment.getEngineer()).willReturn(otherEngineer);
        given(otherEngineer.getId()).willReturn(99L);

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getId()).willReturn(100L);
        given(workReportRepository.existsByAsRequest_Id(100L)).willReturn(false);
        given(asAssignmentRepository.findByAsRequest_Id(100L)).willReturn(List.of(assignment));

        assertThatThrownBy(() -> workReportService.submitWorkReport(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인에게 배정된 A/S 건만 보고서를 작성할 수 있습니다");
    }

    @Test
    @DisplayName("실패: 이미 제출된 보고서 (중복 제출)")
    void submitWorkReport_Fail_AlreadySubmitted() throws Exception {
        User engineer = mock(User.class);
        AsRequest asRequest = mock(AsRequest.class);
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);
        AsAssignment assignment = validAssignment();

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getId()).willReturn(100L);
        given(asAssignmentRepository.findByAsRequest_Id(100L)).willReturn(List.of(assignment));
        given(workReportRepository.existsByAsRequest_Id(100L)).willReturn(true);

        assertThatThrownBy(() -> workReportService.submitWorkReport(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 제출된 보고서가 존재합니다");
    }

    private AsAssignment validAssignment() {
        AsAssignment assignment = mock(AsAssignment.class);
        User assignedEngineer = mock(User.class);
        given(assignment.getEngineer()).willReturn(assignedEngineer);
        given(assignedEngineer.getId()).willReturn(1L);
        given(assignment.getStatus()).willReturn("ACCEPTED");
        return assignment;
    }

    private CreateWorkReportRequest createReportRequest(String diag, List<CreateWorkReportRequest.PartDto> parts) throws Exception {
        Constructor<CreateWorkReportRequest> constructor = CreateWorkReportRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreateWorkReportRequest req = constructor.newInstance();
        ReflectionTestUtils.setField(req, "requestId", 100L);
        ReflectionTestUtils.setField(req, "diagnosisResult", diag);
        ReflectionTestUtils.setField(req, "workDurationMin", 120);
        ReflectionTestUtils.setField(req, "finalAmount", 200000);
        ReflectionTestUtils.setField(req, "memo", "메모");
        ReflectionTestUtils.setField(req, "parts", parts);
        return req;
    }

    @Test
    @DisplayName("성공: 고객이 본인 가전의 수리 이력 조회 (정상 DTO 변환 확인)")
    void getApplianceRepairHistory_Customer_Success() throws Exception {
        Long customerId = 1L;
        Long applianceId = 100L;
        Appliance appliance = mock(Appliance.class);
        User customer = mock(User.class);

        given(applianceRepository.findById(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(customer);
        given(customer.getId()).willReturn(customerId);

        WorkReport report = mock(WorkReport.class);
        AsRequest asRequest = mock(AsRequest.class);
        Symptom symptom = mock(Symptom.class);
        User engineer = mock(User.class);

        given(report.getReportId()).willReturn(10L);
        given(report.getSubmittedAt()).willReturn(LocalDateTime.now());
        given(report.getAsRequest()).willReturn(asRequest);
        given(asRequest.getSymptom()).willReturn(symptom);
        given(symptom.getSymptomName()).willReturn("냉방 불량");
        given(report.getEngineer()).willReturn(engineer);
        given(engineer.getName()).willReturn("김기사");
        given(report.getDiagnosisResult()).willReturn(com.careflow.report.domain.enums.DiagnosisResult.REPAIRED);
        given(report.getFinalAmount()).willReturn(50000);

        given(workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(applianceId)).willReturn(List.of(report));

        List<RepairHistoryResponse> result = workReportService.getApplianceRepairHistory(customerId, "CUSTOMER", applianceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReportId()).isEqualTo(10L);
        assertThat(result.get(0).getSymptomName()).isEqualTo("냉방 불량");
    }

    @Test
    @DisplayName("실패: 고객이 타인의 가전 수리 이력을 몰래 조회 시도 (권한 방어)")
    void getApplianceRepairHistory_Fail_NotOwner() throws Exception {
        Long myId = 1L;
        Long otherId = 2L;
        Long applianceId = 100L;
        Appliance appliance = mock(Appliance.class);
        User otherCustomer = mock(User.class);

        given(applianceRepository.findById(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(otherCustomer);
        given(otherCustomer.getId()).willReturn(otherId);

        assertThatThrownBy(() -> workReportService.getApplianceRepairHistory(myId, "CUSTOMER", applianceId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인 소유의 가전제품 수리 이력만 조회할 수 있습니다.");
    }

    @Test
    @DisplayName("성공: 고객이 본인의 작업 보고서를 상세 조회한다")
    void getWorkReportDetail_Success() throws Exception {
        Long customerId = 1L;
        Long reportId = 10L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        User engineer = mock(User.class);
        given(engineer.getName()).willReturn("테스트기사");

        // 🌟 핵심 수정 포인트: DTO 변환 시 필요한 Appliance와 Symptom 추가 모킹
        Appliance appliance = mock(Appliance.class);
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("비스포크");

        Symptom symptom = mock(Symptom.class);
        given(symptom.getSymptomName()).willReturn("소음");

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getId()).willReturn(100L);
        given(asRequest.getCustomer()).willReturn(customer);
        given(asRequest.getAppliance()).willReturn(appliance); // 필수!
        given(asRequest.getSymptom()).willReturn(symptom); // 필수!

        WorkReport report = mock(WorkReport.class);
        given(report.getReportId()).willReturn(reportId);
        given(report.getAsRequest()).willReturn(asRequest);
        given(report.getEngineer()).willReturn(engineer);
        given(report.getDiagnosisResult()).willReturn(com.careflow.report.domain.enums.DiagnosisResult.NORMAL);
        given(report.getParts()).willReturn(List.of());

        given(workReportRepository.findByIdWithParts(reportId)).willReturn(Optional.of(report));

        WorkReportDetailResponse response = workReportService.getWorkReportDetail(customerId, "CUSTOMER", reportId);

        assertThat(response.getReportId()).isEqualTo(reportId);
        assertThat(response.getEngineerName()).isEqualTo("테스트기사");
    }

    @Test
    @DisplayName("성공: 고객이 본인의 작업 보고서를 승인한다 (customer_approved 상태 변경)")
    void approveWorkReport_Success() throws Exception {
        Long customerId = 1L;
        Long reportId = 10L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(customer);

        WorkReport report = mock(WorkReport.class);
        given(report.getAsRequest()).willReturn(asRequest);
        given(report.isCustomerApproved()).willReturn(false);

        given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

        workReportService.approveWorkReport(customerId, reportId);
        verify(report).approveByCustomer();
    }

    @Test
    @DisplayName("실패: 타인의 보고서를 승인하려 할 때 예외 발생")
    void approveWorkReport_Fail_NotOwner() throws Exception {
        Long myId = 1L;
        Long otherPersonId = 2L;
        Long reportId = 10L;

        User otherCustomer = mock(User.class);
        given(otherCustomer.getId()).willReturn(otherPersonId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(otherCustomer);

        WorkReport report = mock(WorkReport.class);
        given(report.getAsRequest()).willReturn(asRequest);

        given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> workReportService.approveWorkReport(myId, reportId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인의 A/S 보고서만 승인할 수 있습니다.");
    }

    @Nested
    @DisplayName("getEngineerWorkReports — 기사 본인의 작업 보고서 목록 페이징 조회")
    class GetEngineerWorkReports {

        private AsAssignment createMockAssignment(boolean hasReport, boolean isApproved) {
            AsAssignment assignment = mock(AsAssignment.class);
            AsRequest req = mock(AsRequest.class);
            Appliance app = mock(Appliance.class);
            User customer = mock(User.class);

            given(assignment.getAsRequest()).willReturn(req);
            given(req.getCustomer()).willReturn(customer);
            given(req.getAppliance()).willReturn(app);
            given(req.getId()).willReturn(1L);
            given(req.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 18, 10, 0));
            given(req.getScheduledDate()).willReturn(LocalDate.of(2024, 6, 18));
            given(req.getScheduledTime()).willReturn("14:00");
            given(customer.getName()).willReturn("테스트고객");
            given(app.getBrand()).willReturn("삼성");
            given(app.getModelName()).willReturn("에어컨");

            if (hasReport) {
                WorkReport report = mock(WorkReport.class);
                given(report.getReportId()).willReturn(10L);
                given(report.getDiagnosisResult()).willReturn(com.careflow.report.domain.enums.DiagnosisResult.REPAIRED);
                given(report.getFinalAmount()).willReturn(50000);
                given(report.isCustomerApproved()).willReturn(isApproved);
                given(report.getSubmittedAt()).willReturn(LocalDateTime.of(2024, 6, 18, 15, 30));

                if (isApproved) {
                    given(report.getApprovedAt()).willReturn(LocalDateTime.of(2024, 6, 18, 16, 0));
                }
                given(req.getWorkReport()).willReturn(report);
            } else {
                given(req.getWorkReport()).willReturn(null);
            }
            return assignment;
        }

        @Test
        @DisplayName("성공: 보고서가 없는 배차는 DRAFT 상태로 매핑된다")
        void success_draftStatus() {
            AsAssignment draftAssignment = createMockAssignment(false, false);
            PageRequest pageRequest = PageRequest.of(0, 20);
            Page<AsAssignment> mockPage = new PageImpl<>(List.of(draftAssignment), pageRequest, 1);

            given(asAssignmentRepository.findWorkReportListByEngineerId(1L, pageRequest)).willReturn(mockPage);

            Page<EngineerReportListResponse> result = workReportService.getEngineerWorkReports(1L, pageRequest);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("성공: 보고서가 제출되었으나 미승인이면 SUBMITTED 상태로 매핑된다")
        void success_submittedStatus() {
            AsAssignment submittedAssignment = createMockAssignment(true, false);
            PageRequest pageRequest = PageRequest.of(0, 20);
            Page<AsAssignment> mockPage = new PageImpl<>(List.of(submittedAssignment), pageRequest, 1);

            given(asAssignmentRepository.findWorkReportListByEngineerId(1L, pageRequest)).willReturn(mockPage);

            Page<EngineerReportListResponse> result = workReportService.getEngineerWorkReports(1L, pageRequest);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("성공: 보고서가 제출되고 승인 완료되면 APPROVED 상태로 매핑된다")
        void success_approvedStatus() {
            AsAssignment approvedAssignment = createMockAssignment(true, true);
            PageRequest pageRequest = PageRequest.of(0, 20);
            Page<AsAssignment> mockPage = new PageImpl<>(List.of(approvedAssignment), pageRequest, 1);

            given(asAssignmentRepository.findWorkReportListByEngineerId(1L, pageRequest)).willReturn(mockPage);

            Page<EngineerReportListResponse> result = workReportService.getEngineerWorkReports(1L, pageRequest);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("APPROVED");
        }
    }

    @Nested
    @DisplayName("cancelApprovalRequest — 작업 보고서 승인 요청 취소")
    class CancelApprovalRequest {

        @Test
        @DisplayName("성공: 미승인 보고서 취소 시 AsRequest 상태가 IN_PROGRESS로 원복되고 보고서가 삭제된다.")
        void success_revertAndDeleted() {
            Long engineerId = 10L;
            Long reportId = 100L;

            User engineer = mock(User.class);
            given(engineer.getId()).willReturn(engineerId);

            AsRequest asRequest = mock(AsRequest.class);
            given(asRequest.getStatus()).willReturn(AsStatus.COMPLETED);

            WorkReport report = mock(WorkReport.class);
            given(report.getEngineer()).willReturn(engineer);
            given(report.getAsRequest()).willReturn(asRequest);
            given(report.isCustomerApproved()).willReturn(false);

            given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

            workReportService.cancelApprovalRequest(engineerId, reportId);

            verify(asRequest).revertToInProgress();
            verify(asStatusLogRepository).save(any(AsStatusLog.class));
            verify(workReportRepository).delete(report);
        }

        @Test
        @DisplayName("실패: 타인의 보고서를 취소하려고 하면 예외 발생")
        void fail_notOwnedReport() {
            Long myEngineerId = 10L;
            Long otherEngineerId = 99L;
            Long reportId = 100L;

            User otherEngineer = mock(User.class);
            given(otherEngineer.getId()).willReturn(otherEngineerId);

            WorkReport report = mock(WorkReport.class);
            given(report.getEngineer()).willReturn(otherEngineer);

            given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

            assertThatThrownBy(() -> workReportService.cancelApprovalRequest(myEngineerId, reportId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("본인이 작성한 보고서만 취소할 수 있습니다.");
        }

        @Test
        @DisplayName("실패: 이미 고객이 승인한 보고서는 취소 불가능")
        void fail_alreadyApproved() {
            Long engineerId = 10L;
            Long reportId = 100L;

            User engineer = mock(User.class);
            given(engineer.getId()).willReturn(engineerId);

            WorkReport report = mock(WorkReport.class);
            given(report.getEngineer()).willReturn(engineer);
            given(report.isCustomerApproved()).willReturn(true);

            given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

            assertThatThrownBy(() -> workReportService.cancelApprovalRequest(engineerId, reportId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 고객이 승인한 보고서는 취소할 수 없습니다.");
        }
    }
}