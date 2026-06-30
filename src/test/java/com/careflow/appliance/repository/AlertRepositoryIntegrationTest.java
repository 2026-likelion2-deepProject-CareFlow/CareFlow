package com.careflow.appliance.repository;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.entity.ConsumableAlert;
import com.careflow.common.enums.ApplianceStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("Quartz 알림 전용 Repository 통합 테스트 (H2)")
class AlertRepositoryIntegrationTest {

    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private ConsumableAlertRepository consumableAlertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;

    private User customer;
    private ApplianceCategory category;
    private LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        customer = userRepository.save(User.builder()
                .email("alert@test.com").passwordHash("hash").name("알림고객")
                .role(Role.CUSTOMER).build());

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("대형가전", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("공기청정기", rootCat, 1));
    }

    @Nested
    @DisplayName("ApplianceRepository - 무상 A/S 만료 대상 조회")
    class FindByWarrantyEndDateWithUser {

        @Test
        @DisplayName("성공: 만료일이 정확히 일치하는 활성 가전만 반환")
        void success_exactDate_and_notSold() {
            LocalDate targetDate = today.plusDays(30);

            // 1. 대상 가전 (정상)
            applianceRepository.save(Appliance.create(
                    customer, category, "LG", "모델A", null, null, targetDate, RegisterMethod.MANUAL));

            // 2. 만료일이 다른 가전 (제외)
            applianceRepository.save(Appliance.create(
                    customer, category, "LG", "모델B", null, null, targetDate.plusDays(1), RegisterMethod.MANUAL));

            // 3. 만료일은 같으나 '판매됨(SOLD)' 가전 (제외)
            Appliance soldAppliance = Appliance.create(
                    customer, category, "LG", "모델C", null, null, targetDate, RegisterMethod.MANUAL);
            soldAppliance.changeStatus(ApplianceStatus.SOLD);
            applianceRepository.save(soldAppliance);

            // When
            List<Appliance> result = applianceRepository.findByWarrantyEndDateWithUser(targetDate);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getModelName()).isEqualTo("모델A");
            assertThat(result.get(0).getUser().getName()).isEqualTo("알림고객"); // JOIN FETCH 검증
        }
    }

    @Nested
    @DisplayName("ConsumableAlertRepository - 소모품 교체 주기 도래 조회")
    class FindAlertsToNotify {

        @Test
        @DisplayName("성공: 오늘 날짜 이하(<=)이며 활성화(is_active=true)된 소모품만 반환")
        void success_lessThanOrEqualToday_and_active() {
            Appliance appliance = applianceRepository.save(Appliance.create(
                    customer, category, "다이슨", "모델D", null, null, null, RegisterMethod.MANUAL));

            // 1. 오늘이 알림일인 활성 소모품 (대상)
            consumableAlertRepository.save(ConsumableAlert.builder()
                    .appliance(appliance).user(customer).consumableName("필터A")
                    .cycleMonths(6).nextAlertDate(today).build());

            // 2. 어제가 알림일이었던 활성 소모품 (배치 누락 대비 방어 대상 - 포함되어야 함)
            consumableAlertRepository.save(ConsumableAlert.builder()
                    .appliance(appliance).user(customer).consumableName("필터B")
                    .cycleMonths(3).nextAlertDate(today.minusDays(1)).build());

            // 3. 내일이 알림일인 소모품 (제외)
            consumableAlertRepository.save(ConsumableAlert.builder()
                    .appliance(appliance).user(customer).consumableName("필터C")
                    .cycleMonths(6).nextAlertDate(today.plusDays(1)).build());

            // 4. 알림일은 오늘이나 비활성화(isActive=false)된 소모품 (제외)
            ConsumableAlert inactiveAlert = ConsumableAlert.builder()
                    .appliance(appliance).user(customer).consumableName("필터D")
                    .cycleMonths(6).nextAlertDate(today).build();
            inactiveAlert.toggleActive(false);
            consumableAlertRepository.save(inactiveAlert);

            // When
            List<ConsumableAlert> result = consumableAlertRepository.findAlertsToNotify(today);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("consumableName").containsExactlyInAnyOrder("필터A", "필터B");
        }
    }
}