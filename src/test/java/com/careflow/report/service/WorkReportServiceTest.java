package com.careflow.report.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkReportService 단위 테스트 (Mock 기반 완전 분리)")
class WorkReportServiceTest {

    @InjectMocks private WorkReportService workReportService;

    @Mock private WorkReportRepository workReportRepository;
    @Mock private RepairPartRepository repairPartRepository;
    @Mock private HealthCertificateRepository healthCertificateRepository;
    @Mock private AsRequestRepository asRequestRepository;
    @Mock private UserRepository userRepository;

    @Test
    @DisplayName("성공: 부품 교체 없음 -> A등급 95점으로 진단서 갱신 (메서드 호출 검증)")
    void submitWorkReport_NoParts_Success() throws Exception {
        // Given (모든 객체를 Mock으로 생성하여 종속성 완전 제거)
        User engineer = mock(User.class);
        AsRequest asRequest = mock(AsRequest.class);
        Appliance appliance = mock(Appliance.class);
        HealthCertificate certificate = mock(HealthCertificate.class);
        WorkReport savedReport = mock(WorkReport.class);

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.of(asRequest));
        given(asRequest.getAppliance()).willReturn(appliance);
        given(appliance.getId()).willReturn(200L);
        given(healthCertificateRepository.findByAppliance_Id(200L)).willReturn(Optional.of(certificate));

        given(savedReport.getReportId()).willReturn(999L);
        given(workReportRepository.save(any(WorkReport.class))).willReturn(savedReport);

        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        // When
        Long reportId = workReportService.submitWorkReport(1L, request);

        // Then
        assertThat(reportId).isEqualTo(999L);
        verify(asRequest).completeWork(); // 상태 변경 메서드가 1번 호출되었는지 검증!
        verify(certificate).updateHealthGrade("A", 95, false); // A등급 업데이트 메서드 호출 검증!
    }

    @Test
    @DisplayName("실패: 존재하지 않는 A/S 신청 건")
    void submitWorkReport_Fail_NoRequest() throws Exception {
        // Given
        User engineer = mock(User.class);
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);
        ReflectionTestUtils.setField(request, "requestId", 100L);

        given(userRepository.findById(1L)).willReturn(Optional.of(engineer));
        given(asRequestRepository.findById(100L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> workReportService.submitWorkReport(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 A/S 신청 건");
    }

    // ---------- 픽스처 헬퍼 ----------
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
}