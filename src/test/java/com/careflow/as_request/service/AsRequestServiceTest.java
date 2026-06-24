package com.careflow.as_request.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.Role;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// 중첩 클래스별 @BeforeEach 에서 사전 선언한 stub 중 해당 테스트에서 쓰이지 않는 것이 생길 수 있어 LENIENT 적용
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AsRequestService 테스트")
class AsRequestServiceTest {

    @InjectMocks
    private AsRequestService asRequestService;

    @Mock private AsRequestRepository asRequestRepository;
    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplianceRepository applianceRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private SymptomRepository symptomRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ── 공통 픽스처 ──────────────────────────────
    private User customer;
    private User engineer;
    private Agencies agency;
    private Appliance appliance;
    private ApplianceCategory category;
    private Regions region;
    private Symptom symptom;
    private EngineerProfile engineerProfile;
    private AsRequest savedAsRequest;
    private AsAssignment savedAssignment;

    @BeforeEach
    void setUp() {
        agency = mock(Agencies.class);
        given(agency.getId()).willReturn(1L);

        customer = mock(User.class);
        given(customer.getId()).willReturn(1L);
        given(customer.getRole()).willReturn(Role.CUSTOMER);

        engineer = mock(User.class);
        given(engineer.getId()).willReturn(5L);
        given(engineer.getRole()).willReturn(Role.ENGINEER);
        given(engineer.getAgency()).willReturn(agency);

        category = mock(ApplianceCategory.class);
        given(category.getCategoryId()).willReturn(2);

        appliance = mock(Appliance.class);
        given(appliance.getId()).willReturn(1L);
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getCategory()).willReturn(category);

        region = mock(Regions.class);
        given(region.getId()).willReturn(10);

        // v5 스키마: Symptom 엔티티 mock
        symptom = mock(Symptom.class);
        given(symptom.getId()).willReturn(1L);
        given(symptom.getSymptomCode()).willReturn("COOLING_FAIL");

        engineerProfile = mock(EngineerProfile.class);
        given(engineerProfile.getUser()).willReturn(engineer);

        // save() 가 반환하는 mock 엔티티 — 서비스는 이 반환값에서 id 를 추출함
        savedAsRequest = mock(AsRequest.class);
        given(savedAsRequest.getId()).willReturn(10L);
        given(savedAsRequest.getCustomer()).willReturn(customer);

        savedAssignment = mock(AsAssignment.class);
        given(savedAssignment.getId()).willReturn(20L);
    }

    /** 공통 연관 엔티티 조회 stubbing */
    private void stubCommonLookups() {
        given(userRepository.findById(1L)).willReturn(Optional.of(customer));
        given(applianceRepository.findById(1L)).willReturn(Optional.of(appliance));
        given(symptomRepository.findById(1L)).willReturn(Optional.of(symptom));
        given(regionRepository.findById(10)).willReturn(Optional.of(region));
        given(asRequestRepository.save(any())).willReturn(savedAsRequest);
        given(asAssignmentRepository.save(any())).willReturn(savedAssignment);
    }

    // ─────────────────────────────────────────────
    //  AUTO 배정
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("AUTO 배정")
    class AutoAssignment {

        private AsRequestCreateDto autoDto;

        @BeforeEach
        void setUp() throws Exception {
            // v5 스키마: symptomCode → symptomId
            autoDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "AUTO"
                    }
                    """, AsRequestCreateDto.class);
        }

        @Test
        @DisplayName("성공: Fallback 0 (모든 조건) 에서 기사 매칭 → requestId + assignmentId 반환")
        void auto_fullCondition_success() {
            stubCommonLookups();
            given(engineerProfileRepository.findByAllConditions(
                    any(), any(), eq(ScheduleStatus.AVAILABLE), any(), any(), any()))
                    .willReturn(List.of(engineerProfile));

            AsRequestCreateResponseDto result = asRequestService.createAsRequest(1L, autoDto);

            assertThat(result.requestId()).isEqualTo(10L);
            assertThat(result.assignmentId()).isEqualTo(20L);
            // Fallback 1 이후 쿼리는 호출 안 됨
            verify(engineerProfileRepository, never()).findWithoutBrand(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: Fallback 1 (브랜드 조건 완화) 에서 기사 매칭")
        void auto_fallback1_brandRelaxed_success() {
            stubCommonLookups();
            given(engineerProfileRepository.findByAllConditions(any(), any(), any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findWithoutBrand(any(), any(), eq(ScheduleStatus.AVAILABLE), any(), any()))
                    .willReturn(List.of(engineerProfile));

            AsRequestCreateResponseDto result = asRequestService.createAsRequest(1L, autoDto);

            assertThat(result.assignmentId()).isEqualTo(20L);
            verify(engineerProfileRepository, never()).findWithoutBrandAndRegion(any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: Fallback 2 (브랜드 + 지역 조건 완화) 에서 기사 매칭")
        void auto_fallback2_brandAndRegionRelaxed_success() {
            stubCommonLookups();
            given(engineerProfileRepository.findByAllConditions(any(), any(), any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findWithoutBrand(any(), any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findWithoutBrandAndRegion(any(), any(), eq(ScheduleStatus.AVAILABLE), any()))
                    .willReturn(List.of(engineerProfile));

            AsRequestCreateResponseDto result = asRequestService.createAsRequest(1L, autoDto);

            assertThat(result.assignmentId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("실패: 모든 Fallback 소진 후 기사 없음 → IllegalStateException")
        void auto_allFallbackExhausted_throwsException() {
            stubCommonLookups();
            given(engineerProfileRepository.findByAllConditions(any(), any(), any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findWithoutBrand(any(), any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findWithoutBrandAndRegion(any(), any(), any(), any()))
                    .willReturn(List.of());
            given(engineerProfileRepository.findByScheduleOnly(any(), any(), any()))
                    .willReturn(List.of());

            assertThatThrownBy(() -> asRequestService.createAsRequest(1L, autoDto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("가용한 수리 기사가 없습니다");
        }

        @Test
        @DisplayName("성공: 후보 2명일 때 복합 점수 높은 기사 선택 — 대기 배차 많은 기사는 탈락")
        void auto_compositeScore_selectsLowerLoadEngineer() {
            // engineer1: avgRating=5.0, pendingCount=5 → score = 5.0×0.7 - 5×0.3 = 3.5 - 1.5 = 2.0
            // engineer2: avgRating=4.0, pendingCount=0 → score = 4.0×0.7 - 0×0.3 = 2.8 - 0.0 = 2.8 ← 선택
            User engineer1 = mock(User.class);
            given(engineer1.getId()).willReturn(10L);
            given(engineer1.getAgency()).willReturn(agency);

            User engineer2 = mock(User.class);
            given(engineer2.getId()).willReturn(11L);
            given(engineer2.getAgency()).willReturn(agency);

            EngineerProfile profile1 = mock(EngineerProfile.class);
            given(profile1.getUser()).willReturn(engineer1);
            given(profile1.getAvgRating()).willReturn(new java.math.BigDecimal("5.0"));

            EngineerProfile profile2 = mock(EngineerProfile.class);
            given(profile2.getUser()).willReturn(engineer2);
            given(profile2.getAvgRating()).willReturn(new java.math.BigDecimal("4.0"));

            stubCommonLookups();
            given(engineerProfileRepository.findByAllConditions(any(), any(), any(), any(), any(), any()))
                    .willReturn(List.of(profile1, profile2));

            // engineer1 은 대기 5건, engineer2 는 대기 0건
            given(asAssignmentRepository.countByEngineer_IdAndStatus(10L, "WAITING")).willReturn(5L);
            given(asAssignmentRepository.countByEngineer_IdAndStatus(11L, "WAITING")).willReturn(0L);

            asRequestService.createAsRequest(1L, autoDto);

            // engineer2 로 배정됐는지 검증 — save 호출 시 engineer2 가 포함된 AsAssignment 전달
            org.mockito.ArgumentCaptor<AsAssignment> captor =
                    org.mockito.ArgumentCaptor.forClass(AsAssignment.class);
            verify(asAssignmentRepository).save(captor.capture());
            assertThat(captor.getValue().getEngineer().getId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("실패: 방문 예약 시간 형식 오류 → IllegalArgumentException")
        void auto_invalidTimeFormat_throwsException() throws Exception {
            AsRequestCreateDto badTimeDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "25:99",
                      "assignType": "AUTO"
                    }
                    """, AsRequestCreateDto.class);

            stubCommonLookups();

            assertThatThrownBy(() -> asRequestService.createAsRequest(1L, badTimeDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("시간 형식이 올바르지 않습니다");
        }
    }

    // ─────────────────────────────────────────────
    //  MANUAL 배정
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("MANUAL 배정")
    class ManualAssignment {

        private AsRequestCreateDto manualDto;

        @BeforeEach
        void setUp() throws Exception {
            // v5 스키마: symptomCode → symptomId
            manualDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "MANUAL",
                      "preferredEngineerId": 5
                    }
                    """, AsRequestCreateDto.class);
        }

        @Test
        @DisplayName("성공: ENGINEER 역할 사용자 지정 → 배정 완료")
        void manual_success() {
            stubCommonLookups();
            given(userRepository.findById(5L)).willReturn(Optional.of(engineer));

            AsRequestCreateResponseDto result = asRequestService.createAsRequest(1L, manualDto);

            assertThat(result.requestId()).isEqualTo(10L);
            assertThat(result.assignmentId()).isEqualTo(20L);
            // AUTO 쿼리 미호출 검증
            verify(engineerProfileRepository, never()).findByAllConditions(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("실패: preferredEngineerId 미입력 → IllegalArgumentException")
        void manual_noPreferredEngineerId_throwsException() throws Exception {
            AsRequestCreateDto noEngineerDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "MANUAL"
                    }
                    """, AsRequestCreateDto.class);

            given(userRepository.findById(1L)).willReturn(Optional.of(customer));
            given(applianceRepository.findById(1L)).willReturn(Optional.of(appliance));
            given(symptomRepository.findById(1L)).willReturn(Optional.of(symptom));
            given(regionRepository.findById(10)).willReturn(Optional.of(region));
            given(asRequestRepository.save(any())).willReturn(savedAsRequest);

            assertThatThrownBy(() -> asRequestService.createAsRequest(1L, noEngineerDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수리 기사 선택은 필수");
        }

        @Test
        @DisplayName("실패: 지정한 사용자가 ENGINEER 역할이 아님 → IllegalArgumentException")
        void manual_notEngineerRole_throwsException() {
            User nonEngineer = mock(User.class);
            given(nonEngineer.getRole()).willReturn(Role.CUSTOMER);

            given(userRepository.findById(1L)).willReturn(Optional.of(customer));
            given(applianceRepository.findById(1L)).willReturn(Optional.of(appliance));
            given(symptomRepository.findById(1L)).willReturn(Optional.of(symptom));
            given(regionRepository.findById(10)).willReturn(Optional.of(region));
            given(asRequestRepository.save(any())).willReturn(savedAsRequest);
            given(userRepository.findById(5L)).willReturn(Optional.of(nonEngineer));

            assertThatThrownBy(() -> asRequestService.createAsRequest(1L, manualDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수리 기사가 아닙니다");
        }
    }
}
