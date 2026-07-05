package com.careflow.user.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.region.entity.Regions;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.dto.EngineerCustomerDetailResponse;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineerCustomerService 단위 테스트 (STEP 2)")
class EngineerCustomerServiceTest {

    @InjectMocks private EngineerCustomerService engineerCustomerService;
    @Mock private UserRepository userRepository;
    @Mock private WorkReportRepository workReportRepository;

    @Test
    @DisplayName("성공: 고객의 기본 정보와 해당 기사가 처리한 A/S 이력을 정상적으로 매핑하여 반환한다.")
    void getCustomerDetail_Success() {
        // Given
        Long engineerId = 1L;
        Long customerId = 100L;

        Regions region = mock(Regions.class);
        given(region.getName()).willReturn("서울 강남구");

        User customer = User.builder().name("김고객").phone("010-1234-5678").regionId(region).build();
        ReflectionTestUtils.setField(customer, "id", customerId);
        ReflectionTestUtils.setField(customer, "addressDetail", "101동 101호");

        given(userRepository.findById(customerId)).willReturn(Optional.of(customer));

        // Mock WorkReport (수리 이력)
        WorkReport report = mock(WorkReport.class);
        AsRequest request = mock(AsRequest.class);
        Appliance appliance = mock(Appliance.class);
        Symptom symptom = mock(Symptom.class);

        given(report.getReportId()).willReturn(50L);
        given(report.getSubmittedAt()).willReturn(LocalDateTime.of(2026, 7, 2, 14, 0));
        given(report.getDiagnosisResult()).willReturn(DiagnosisResult.REPAIRED);
        given(report.getFinalAmount()).willReturn(30000);
        given(report.getAsRequest()).willReturn(request);

        given(request.getId()).willReturn(500L);
        given(request.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0));
        given(request.getAppliance()).willReturn(appliance);
        given(request.getSymptom()).willReturn(symptom);

        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("비스포크");
        given(symptom.getSymptomName()).willReturn("소음 발생");

        given(workReportRepository.findHistoryByEngineerAndCustomer(engineerId, customerId))
                .willReturn(List.of(report));

        // When
        EngineerCustomerDetailResponse response = engineerCustomerService.getCustomerDetail(engineerId, customerId);

        // Then
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getName()).isEqualTo("김고객");
        assertThat(response.getRegion()).isEqualTo("서울 강남구");

        // 이력 검증
        assertThat(response.getAsHistory()).hasSize(1);
        assertThat(response.getAsHistory().get(0).getRequestId()).isEqualTo("AS-20260701-0500"); // 포맷팅 검증
        assertThat(response.getAsHistory().get(0).getProductName()).isEqualTo("삼성 비스포크");
    }
}