package com.careflow.engineer.service;

import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page; // 🌟 추가
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineerDashboardService 단위 테스트")
class EngineerDashboardServiceTest {

    @InjectMocks private EngineerDashboardService engineerDashboardService;

    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AsStatusLogRepository asStatusLogRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private ReviewRepository reviewRepository;

    @Test
    @DisplayName("성공: 대시보드 데이터를 정상적으로 조립하여 반환한다.")
    void getDashboardData_Success() {
        // Given
        Long engineerId = 1L;
        User user = User.builder().name("김기사").build();
        EngineerProfile profile = EngineerProfile.createInitial(user);
        ReflectionTestUtils.setField(profile, "skillLevel", SkillLevel.ADVANCED);

        given(engineerProfileRepository.findByUser_Id(engineerId)).willReturn(Optional.of(profile));
        given(asAssignmentRepository.findTodayAssignments(any(), any())).willReturn(Collections.emptyList());
        given(settlementRepository.sumExpectedEarningByEngineerIdAndDate(any(), any(), any())).willReturn(500000);

        // 🌟 수정: Collections.emptyList() 대신 Page.empty()를 사용!
        given(notificationRepository.findByUser_IdOrderByCreatedAtDesc(any(), any())).willReturn(Page.empty());

        // When
        EngineerDashboardResponse response = engineerDashboardService.getDashboardData(engineerId);

        // Then
        assertThat(response.getEngineerName()).isEqualTo("김기사");
        assertThat(response.getSkillLevel()).isEqualTo("ADVANCED");
        assertThat(response.getThisMonthExpectedEarning()).isEqualTo(500000);
        assertThat(response.getTodayExpectedCount()).isEqualTo(0);
        assertThat(response.getTodayCompletedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("성공: 오늘 여러 배정이 있을 때, '진행 중(IN_PROGRESS 등)'인 건을 currentRequestId로 우선 선택한다.")
    void getDashboardData_CurrentRequestId_Selection() {
        // Given
        Long engineerId = 1L;
        User user = User.builder().name("김기사").build();
        EngineerProfile profile = EngineerProfile.createInitial(user);
        given(engineerProfileRepository.findByUser_Id(engineerId)).willReturn(Optional.of(profile));
        given(settlementRepository.sumExpectedEarningByEngineerIdAndDate(any(), any(), any())).willReturn(0);
        given(notificationRepository.findByUser_IdOrderByCreatedAtDesc(any(), any())).willReturn(Page.empty());

        // 가짜 A/S 요청 2개 생성
        com.careflow.as_request.entity.AsRequest req1 = com.careflow.as_request.entity.AsRequest.builder().build();
        ReflectionTestUtils.setField(req1, "id", 100L);
        com.careflow.as_request.entity.AsRequest req2 = com.careflow.as_request.entity.AsRequest.builder().build();
        ReflectionTestUtils.setField(req2, "id", 200L);

        // 가짜 배정 2개 생성 (둘 다 ACCEPTED 상태)
        com.careflow.assignment.entity.AsAssignment assign1 = com.careflow.assignment.entity.AsAssignment.builder().asRequest(req1).build();
        ReflectionTestUtils.setField(assign1, "status", "ACCEPTED");
        com.careflow.assignment.entity.AsAssignment assign2 = com.careflow.assignment.entity.AsAssignment.builder().asRequest(req2).build();
        ReflectionTestUtils.setField(assign2, "status", "ACCEPTED");

        given(asAssignmentRepository.findTodayAssignments(any(), any())).willReturn(java.util.List.of(assign1, assign2));

        // req1의 상태 로그: WAITING (아직 대기 중)
        com.careflow.as_status_log.entity.AsStatusLog log1 = com.careflow.as_status_log.entity.AsStatusLog.builder().toStatus("WAITING").build();
        given(asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(100L)).willReturn(java.util.List.of(log1));

        // req2의 상태 로그: IN_PROGRESS (실제 작업 중!)
        com.careflow.as_status_log.entity.AsStatusLog log2 = com.careflow.as_status_log.entity.AsStatusLog.builder().toStatus("IN_PROGRESS").build();
        given(asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(200L)).willReturn(java.util.List.of(log2));

        // When
        EngineerDashboardResponse response = engineerDashboardService.getDashboardData(engineerId);

        // Then
        // 실제 작업 중인 req2(ID: 200)가 currentRequestId로 선택되어야 함!
        assertThat(response.getCurrentRequestId()).isEqualTo(200L);
        assertThat(response.getCurrentWorkStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("성공: 실적/정산 요약 조회 시, 월간 비교 데이터와 정확한 카운트(진행/취소)를 반환한다.")
    void getSettlementSummary_Success() {
        // Given
        Long engineerId = 1L;
        EngineerProfile profile = EngineerProfile.createInitial(User.builder().build());
        given(engineerProfileRepository.findByUser_Id(engineerId)).willReturn(Optional.of(profile));

        // 🌟 수정: 파라미터가 5개로 늘어났으므로 any()를 5개로 수정!
        given(asAssignmentRepository.findCompletedAssignmentsWithDetails(any(), any(), any(), any(), any())).willReturn(java.util.List.of());
        given(asAssignmentRepository.countByEngineerAndStatusInPeriod(any(), any(), any(), any())).willReturn(3L); // 예: 진행중 3건
        given(asAssignmentRepository.countRequestsByEngineerAndRequestStatusInPeriod(any(), any(), any(), any())).willReturn(1L); // 예: 취소 1건

        // 정산 스냅샷 모킹 (JPA 인터페이스 프로젝션 모킹)
        com.careflow.settlement.repository.SettlementRepository.MonthlySummaryProjection mockAgg =
                org.mockito.Mockito.mock(com.careflow.settlement.repository.SettlementRepository.MonthlySummaryProjection.class);
        given(mockAgg.getTotalGrossAmount()).willReturn(100000L);
        given(mockAgg.getTotalPlatformFee()).willReturn(10000L);
        given(mockAgg.getTotalAgencyFee()).willReturn(5000L);
        given(mockAgg.getTotalEngineerPayout()).willReturn(85000L);

        // 🌟 수정: 파라미터가 5개로 늘어났으므로 any()를 5개로 수정!
        given(settlementRepository.findEngineerMonthlySummary(any(), any(), any(), any(), any())).willReturn(mockAgg);

        // 월간 비교 데이터 모킹
        given(settlementRepository.sumExpectedEarningByEngineerIdAndDate(any(), any(), any())).willReturn(85000, 50000); // 이번달 85000, 지난달 50000

        // When
        // 🌟 수정: 호출 파라미터 5개로 맞춤 (brand, status 파라미터 추가)
        com.careflow.settlement.dto.EngineerSettlementSummaryResponse response =
                engineerDashboardService.getSettlementSummary(engineerId, null, null, null, null);

        // Then
        // 1. 카운트 검증
        assertThat(response.getInProgressCount()).isEqualTo(3L);
        assertThat(response.getCancelledCount()).isEqualTo(1L);

        // 2. 정산 계산 정합성 검증 (Gross - Platform - Agency = Net)
        assertThat(response.getSettlementSummary().getGrossAmount()).isEqualTo(100000);
        assertThat(response.getSettlementSummary().getEngineerNetAmount()).isEqualTo(85000);

        // 3. 비교 로직 검증 (이번 달 85000 - 지난 달 50000 = 차액 35000)
        assertThat(response.getMonthlyComparison().getThisMonthNetAmount()).isEqualTo(85000);
        assertThat(response.getMonthlyComparison().getPrevMonthNetAmount()).isEqualTo(50000);
        assertThat(response.getMonthlyComparison().getNetAmountDiff()).isEqualTo(35000);
    }
}