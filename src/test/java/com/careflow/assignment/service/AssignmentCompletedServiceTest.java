package com.careflow.assignment.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentCompletedResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentCompletedService 단위 테스트 (Mock 기반)")
class AssignmentCompletedServiceTest {

    @InjectMocks
    private AssignmentCompletedService assignmentCompletedService;

    @Mock private AsAssignmentRepository    asAssignmentRepository;
    @Mock private WorkReportRepository      workReportRepository;
    @Mock private ReviewRepository          reviewRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;

    private static final Long AGENCY_ID     = 10L;
    private static final Long REQUEST_ID    = 100L;
    private static final Long ASSIGNMENT_ID = 1L;
    private static final Long ENGINEER_ID   = 20L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;

    @BeforeEach
    void setUp() {
        agencyUser   = mock(CustomUserDetails.class);
        customerUser = mock(CustomUserDetails.class);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(customerUser.getRole()).willReturn("CUSTOMER");
    }

    private AsAssignment buildAssignmentMock() {
        AsAssignment a     = mock(AsAssignment.class);
        User customer  = mock(User.class);
        User engineer  = mock(User.class);
        Appliance appliance = mock(Appliance.class);
        AsRequest req  = mock(AsRequest.class);

        given(customer.getId()).willReturn(200L);
        given(customer.getName()).willReturn("테스트고객");
        given(customer.getPhone()).willReturn("010-1111-2222");
        given(engineer.getId()).willReturn(ENGINEER_ID);
        given(engineer.getName()).willReturn("테스트기사");
        given(engineer.getPhone()).willReturn("010-3333-4444");
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("에어컨 Q9000");

        given(req.getId()).willReturn(REQUEST_ID);
        given(req.getCustomer()).willReturn(customer);
        given(req.getAppliance()).willReturn(appliance);
        given(req.getScheduledDate()).willReturn(LocalDate.of(2026, 6, 1));
        given(req.getScheduledTime()).willReturn("10:00");
        given(req.getVisitAddressDetail()).willReturn("강남구 테헤란로 123");

        given(a.getId()).willReturn(ASSIGNMENT_ID);
        given(a.getEngineer()).willReturn(engineer);
        given(a.getAsRequest()).willReturn(req);
        given(a.getStatus()).willReturn("COMPLETED");

        return a;
    }

    private WorkReport buildReportMock() {
        WorkReport report = mock(WorkReport.class);
        AsRequest req = mock(AsRequest.class);
        given(req.getId()).willReturn(REQUEST_ID);
        given(report.getAsRequest()).willReturn(req);
        given(report.getSubmittedAt()).willReturn(LocalDateTime.of(2026, 6, 1, 12, 0));
        given(report.getWorkDurationMin()).willReturn(90);
        given(report.getDiagnosisResult()).willReturn(DiagnosisResult.REPAIRED);
        given(report.getFinalAmount()).willReturn(80000);
        given(report.getMemo()).willReturn("냉매 보충 완료");
        return report;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("COMPLETED 배정 없음 → 빈 리스트 반환")
        void getCompleted_empty() throws Exception {
            given(asAssignmentRepository.findCompletedByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            List<AssignmentCompletedResponse> result =
                    assignmentCompletedService.getCompleted(agencyUser);

            assertThat(result).isEmpty();
            verifyNoInteractions(workReportRepository, reviewRepository, engineerProfileRepository);
        }

        @Test
        @DisplayName("COMPLETED 배정 1건 + 작업보고서 있고 리뷰 없음 → rating·reviewContent null 반환")
        void getCompleted_oneAssignment_noReview() throws Exception {
            AsAssignment a = buildAssignmentMock();
            WorkReport report = buildReportMock();

            given(asAssignmentRepository.findCompletedByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(workReportRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of(report));
            given(reviewRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of());

            List<AssignmentCompletedResponse> result =
                    assignmentCompletedService.getCompleted(agencyUser);

            assertThat(result).hasSize(1);
            AssignmentCompletedResponse r = result.get(0);
            assertThat(r.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(r.customerName()).isEqualTo("테스트고객");
            assertThat(r.productName()).isEqualTo("삼성 에어컨 Q9000");
            assertThat(r.workDurationMin()).isEqualTo(90);
            assertThat(r.diagnosisResult()).isEqualTo("REPAIRED");
            assertThat(r.finalAmount()).isEqualTo(80000);
            // 리뷰 없음
            assertThat(r.rating()).isNull();
            assertThat(r.reviewContent()).isNull();
            assertThat(r.reviewAt()).isNull();
        }

        @Test
        @DisplayName("리뷰 존재 → rating·reviewContent·reviewAt 모두 매핑")
        void getCompleted_withReview() throws Exception {
            AsAssignment a = buildAssignmentMock();
            WorkReport report = buildReportMock();
            Review review = mock(Review.class);
            AsRequest req = a.getAsRequest();
            given(review.getAsRequest()).willReturn(req);
            given(review.getRating()).willReturn(5);
            given(review.getContent()).willReturn("친절하고 빠른 수리 감사합니다");
            given(review.getCreatedAt()).willReturn(LocalDateTime.of(2026, 6, 2, 9, 0));

            given(asAssignmentRepository.findCompletedByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(workReportRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of(report));
            given(reviewRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of(review));

            List<AssignmentCompletedResponse> result =
                    assignmentCompletedService.getCompleted(agencyUser);

            AssignmentCompletedResponse r = result.get(0);
            assertThat(r.rating()).isEqualTo(5);
            assertThat(r.reviewContent()).isEqualTo("친절하고 빠른 수리 감사합니다");
            assertThat(r.reviewAt()).isEqualTo(LocalDateTime.of(2026, 6, 2, 9, 0));
        }

        @Test
        @DisplayName("기사 프로필 존재 → engineerRating·engineerImg 매핑")
        void getCompleted_withProfile() throws Exception {
            AsAssignment a = buildAssignmentMock();
            WorkReport report = buildReportMock();
            EngineerProfile profile = mock(EngineerProfile.class);
            User engineerUser = mock(User.class);
            given(engineerUser.getId()).willReturn(ENGINEER_ID);
            given(profile.getUser()).willReturn(engineerUser);
            given(profile.getAvgRating()).willReturn(new BigDecimal("4.8"));
            given(profile.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");

            given(asAssignmentRepository.findCompletedByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of(profile));
            given(workReportRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of(report));
            given(reviewRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of());

            List<AssignmentCompletedResponse> result =
                    assignmentCompletedService.getCompleted(agencyUser);

            AssignmentCompletedResponse r = result.get(0);
            assertThat(r.engineerRating()).isEqualTo(4.8);
            assertThat(r.engineerImg()).isEqualTo("https://cdn.example.com/img.jpg");
        }

        @Test
        @DisplayName("작업보고서 없는 배정 → 결과에서 제외")
        void getCompleted_noReport_excluded() throws Exception {
            AsAssignment a = buildAssignmentMock();

            given(asAssignmentRepository.findCompletedByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(workReportRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of());   // 보고서 없음
            given(reviewRepository.findByAsRequestIdIn(List.of(REQUEST_ID)))
                    .willReturn(List.of());

            List<AssignmentCompletedResponse> result =
                    assignmentCompletedService.getCompleted(agencyUser);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void getCompleted_notAgencyRole_throws() {
            assertThatThrownBy(() -> assignmentCompletedService.getCompleted(customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }
    }
}
