package com.careflow.report.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings; // 🎯 추가
import org.mockito.quality.Strictness;            // 🎯 추가
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 🎯 추가: Mockito의 불필요한 Stubbing 깐깐한 검사 무시
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


    @Test
    @DisplayName("성공: 부품 교체 없음 -> 100점으로 진단서 갱신")
    void submitWorkReport_NoParts_Success() throws Exception {
        User engineer = mock(User.class);
        AsRequest asRequest = mock(AsRequest.class);
        Appliance appliance = mock(Appliance.class);
        HealthCertificate certificate = mock(HealthCertificate.class);
        WorkReport savedReport = mock(WorkReport.class);
        AsAssignment assignment = validAssignment();

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getId()).willReturn(100L);
        given(workReportRepository.existsByAsRequest_Id(100L)).willReturn(false);
        given(asAssignmentRepository.findByAsRequest_Id(100L)).willReturn(List.of(assignment));

        given(asRequest.getAppliance()).willReturn(appliance);
        given(appliance.getId()).willReturn(200L);
        given(healthCertificateRepository.findByAppliance_Id(200L)).willReturn(Optional.of(certificate));

        given(savedReport.getReportId()).willReturn(999L);
        given(workReportRepository.save(any(WorkReport.class))).willReturn(savedReport);

        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        Long reportId = workReportService.submitWorkReport(1L, request);

        assertThat(reportId).isEqualTo(999L);
        verify(asRequest).completeWork();
        verify(certificate).calculateAndUpdateHealth(null, null);
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

        AsAssignment assignment = validAssignment(); // 🎯 1. 정상 배정 내역 세팅

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getId()).willReturn(100L);

        // 🎯 2. 1단계 관문(권한 검증) 무사 통과시키기
        given(asAssignmentRepository.findByAsRequest_Id(100L)).willReturn(List.of(assignment));

        // 🎯 3. 2단계 관문(중복 제출 검사)에서 에러 터뜨리기
        given(workReportRepository.existsByAsRequest_Id(100L)).willReturn(true);

        assertThatThrownBy(() -> workReportService.submitWorkReport(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 제출된 보고서가 존재합니다");
    }

    // ---------- 픽스처 헬퍼 ----------
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
        // Given
        Long customerId = 1L;
        Long applianceId = 100L;
        Appliance appliance = mock(Appliance.class);
        User customer = mock(User.class);

        given(applianceRepository.findById(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(customer);
        given(customer.getId()).willReturn(customerId); // 본인 소유 가전 인증 통과

        // DTO 변환을 위한 딥 모킹(Deep Mocking)
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

        // Repository에서 리스트 반환
        given(workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(applianceId))
                .willReturn(List.of(report));

        // When
        List<RepairHistoryResponse> result = workReportService.getApplianceRepairHistory(customerId, "CUSTOMER", applianceId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReportId()).isEqualTo(10L);
        assertThat(result.get(0).getSymptomName()).isEqualTo("냉방 불량");
        assertThat(result.get(0).getEngineerName()).isEqualTo("김기사");
        assertThat(result.get(0).getFinalAmount()).isEqualTo(50000);
    }

    @Test
    @DisplayName("실패: 고객이 타인의 가전 수리 이력을 몰래 조회 시도 (권한 방어)")
    void getApplianceRepairHistory_Fail_NotOwner() throws Exception {
        // Given
        Long myId = 1L;
        Long otherId = 2L;
        Long applianceId = 100L;
        Appliance appliance = mock(Appliance.class);
        User otherCustomer = mock(User.class);

        given(applianceRepository.findById(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(otherCustomer);
        given(otherCustomer.getId()).willReturn(otherId); // 소유자가 다름!

        // When & Then
        assertThatThrownBy(() -> workReportService.getApplianceRepairHistory(myId, "CUSTOMER", applianceId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인 소유의 가전제품 수리 이력만 조회할 수 있습니다.");
    }

    // =========================================================================
    // 💡 추가된 [작업 보고서 상세 조회 및 고객 승인] 단위 테스트
    // =========================================================================

    @Test
    @DisplayName("성공: 고객이 본인의 작업 보고서를 상세 조회한다")
    void getWorkReportDetail_Success() throws Exception {
        // Given
        Long customerId = 1L;
        Long reportId = 10L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        User engineer = mock(User.class);
        given(engineer.getName()).willReturn("테스트기사");

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getId()).willReturn(100L);
        given(asRequest.getCustomer()).willReturn(customer);

        WorkReport report = mock(WorkReport.class);
        given(report.getReportId()).willReturn(reportId);
        given(report.getAsRequest()).willReturn(asRequest);
        given(report.getEngineer()).willReturn(engineer);
        given(report.getDiagnosisResult()).willReturn(com.careflow.report.domain.enums.DiagnosisResult.NORMAL);
        given(report.getParts()).willReturn(List.of()); // 빈 부품 리스트

        given(workReportRepository.findByIdWithParts(reportId)).willReturn(Optional.of(report));

        // When
        WorkReportDetailResponse response = workReportService.getWorkReportDetail(customerId, "CUSTOMER", reportId);

        // Then
        assertThat(response.getReportId()).isEqualTo(reportId);
        assertThat(response.getEngineerName()).isEqualTo("테스트기사");
    }

    @Test
    @DisplayName("성공: 고객이 본인의 작업 보고서를 승인한다 (customer_approved 상태 변경)")
    void approveWorkReport_Success() throws Exception {
        // Given
        Long customerId = 1L;
        Long reportId = 10L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(customer);

        WorkReport report = mock(WorkReport.class);
        given(report.getAsRequest()).willReturn(asRequest);
        given(report.isCustomerApproved()).willReturn(false); // 아직 승인 안됨

        given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

        // When
        workReportService.approveWorkReport(customerId, reportId);

        // Then
        verify(report).approveByCustomer(); // 도메인 메서드 호출 여부 검증
    }

    @Test
    @DisplayName("실패: 타인의 보고서를 승인하려 할 때 예외 발생")
    void approveWorkReport_Fail_NotOwner() throws Exception {
        Long myId = 1L;
        Long otherPersonId = 2L;
        Long reportId = 10L;

        User otherCustomer = mock(User.class);
        given(otherCustomer.getId()).willReturn(otherPersonId); // 소유자가 다름

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(otherCustomer);

        WorkReport report = mock(WorkReport.class);
        given(report.getAsRequest()).willReturn(asRequest);

        given(workReportRepository.findById(reportId)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> workReportService.approveWorkReport(myId, reportId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인의 A/S 보고서만 승인할 수 있습니다.");
    }
}