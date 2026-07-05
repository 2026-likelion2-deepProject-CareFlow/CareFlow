package com.careflow.appliance.service;

import com.careflow.appliance.dto.HealthCertificateResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.common.enums.PartImportance;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApplianceService 단위 테스트 (Mock 기반)")
class ApplianceServiceTest {

    @InjectMocks
    private ApplianceService applianceService;

    @Mock private ApplianceRepository applianceRepository;
    @Mock private HealthCertificateRepository healthCertificateRepository;
    @Mock private WorkReportRepository workReportRepository;

    @Test
    @DisplayName("성공: 건강 진단서 상세 조회 시 4축 점수가 정확히 역추산되어 반환된다")
    void getHealthCertificate_Success() throws Exception {
        // Given
        Long customerId = 1L;
        Long applianceId = 100L;

        Appliance appliance = mock(Appliance.class);
        User customer = mock(User.class);

        given(applianceRepository.findByIdAndDeletedAtIsNull(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(customer);
        given(customer.getId()).willReturn(customerId);
        given(appliance.getId()).willReturn(applianceId);

        // 2축(사용기간) 테스트용 데이터: 2년 전 구매 -> 20점
        given(appliance.getPurchaseDate()).willReturn(LocalDate.now().minusYears(2));

        HealthCertificate cert = mock(HealthCertificate.class);
        given(healthCertificateRepository.findByAppliance_Id(applianceId)).willReturn(Optional.of(cert));

        // 1축(수리횟수) 테스트용 데이터: 누적 2회 수리 -> 15점 (HealthScoreCalculator: 0회=25,1회=20,2회=15,3회=8,4회+=0)
        given(cert.getRepairCount()).willReturn(2);
        given(cert.getUpdatedAt()).willReturn(LocalDateTime.now());
        given(cert.getGrade()).willReturn("B");
        given(cert.getScore()).willReturn(75);
        given(cert.isCertified()).willReturn(true);
        given(cert.getCertId()).willReturn(10L);

        // 4축(최근 수리일) 및 3축(부품 중요도) 역추산을 위한 Report 모킹
        WorkReport latestReport = mock(WorkReport.class);
        WorkReport olderReport = mock(WorkReport.class);

        // 가장 최근 보고서(get(0)): 13개월 전 제출 -> 4축 15점. 이 보고서엔 부품 교체가 없음
        given(latestReport.getSubmittedAt()).willReturn(LocalDateTime.now().minusMonths(13));
        given(latestReport.getParts()).willReturn(List.of());

        // 3축 부품 중요도 테스트용: 가장 최근이 아닌 이전 보고서에 NORMAL 부품 교체가 있어도 잡혀야 함 -> 15점
        WorkReportPart part = mock(WorkReportPart.class);
        RepairPart repairPart = mock(RepairPart.class);
        given(part.getRepairPart()).willReturn(repairPart);
        given(repairPart.getImportance()).willReturn(PartImportance.NORMAL);
        given(olderReport.getParts()).willReturn(List.of(part));

        given(workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(applianceId))
                .willReturn(List.of(latestReport, olderReport));

        // When
        HealthCertificateResponse response = applianceService.getHealthCertificate(customerId, "CUSTOMER", applianceId);

        // Then: DB 컬럼이 없어도 4축 점수가 완벽하게 계산되어 나와야 함
        assertThat(response.getGrade()).isEqualTo("B");
        assertThat(response.getScore()).isEqualTo(75);

        // 계산식 검증: 15(누적2회) + 20(사용2년) + 15(이전 보고서의 NORMAL 부품) + 15(가장 최근 보고서 13개월 전) = 65점
        assertThat(response.getRepairCountScore()).isEqualTo(15);
        assertThat(response.getUsagePeriodScore()).isEqualTo(20);
        assertThat(response.getPartImportanceScore()).isEqualTo(15);
        assertThat(response.getLastRepairedScore()).isEqualTo(15);
    }

    @Test
    @DisplayName("실패: 타인의 가전제품 건강 진단서를 몰래 조회 시도 시 차단")
    void getHealthCertificate_Fail_NotOwner() {
        // Given
        Long myId = 1L;
        Long otherPersonId = 2L;
        Long applianceId = 100L;

        Appliance appliance = mock(Appliance.class);
        User otherCustomer = mock(User.class);

        given(applianceRepository.findByIdAndDeletedAtIsNull(applianceId)).willReturn(Optional.of(appliance));
        given(appliance.getUser()).willReturn(otherCustomer);
        given(otherCustomer.getId()).willReturn(otherPersonId); // 소유자가 다름

        // When & Then
        assertThatThrownBy(() -> applianceService.getHealthCertificate(myId, "CUSTOMER", applianceId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인 소유의 가전제품 진단서만 조회할 수 있습니다.");
    }
}