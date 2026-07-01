package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyReviewSearchRequest;
import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgencyReviewService 통합 테스트 (H2 DB 연동)
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - @Sql cleanup.sql: 각 테스트 전 데이터 전체 초기화 (FK 제약 일시 해제)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyReviewService 통합 테스트 (H2 DB 연동)")
class AgencyReviewServiceIntegrationTest {

    @Autowired private AgencyReviewService agencyReviewService;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private ReviewRepository reviewRepository;

    private Agencies agency;
    private Agencies otherAgency;
    private User agencyUser;
    private User engineerUser;
    private User otherEngineerUser;
    private ApplianceCategory leafCategory;
    private Regions district;
    private Symptom symptom;

    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        agency = agencyRepository.save(Agencies.create("테스트대행사", "123-45-67890", "서울시 강남구", 5.0));
        otherAgency = agencyRepository.save(Agencies.create("타대행사", "999-99-99999", "서울시 서초구", 5.0));

        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("hashed")
                .name("대행사담당자").phone("010-9999-9999")
                .role(Role.AGENCY).agency(agency).build());

        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("테스트기사").phone("010-1234-5678")
                .role(Role.ENGINEER).agency(agency).build());

        otherEngineerUser = userRepository.save(User.builder()
                .email("other-engineer@test.com").passwordHash("hashed")
                .name("타대행사기사").phone("010-0000-0000")
                .role(Role.ENGINEER).agency(otherAgency).build());

        ApplianceCategory rootCategory = categoryRepository.save(ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(ApplianceCategory.createChild("에어컨", rootCategory, 1));

        district = regionRepository.save(Regions.create("강남구", null, 2, 0));

        symptom = symptomRepository.save(Symptom.builder()
                .category(leafCategory).symptomCode("TEST_FAIL").symptomName("테스트 증상").build());
    }

    private CustomUserDetails agencyUserDetails() {
        return new CustomUserDetails(agencyUser.getId(), agencyUser.getEmail(), "pw", "AGENCY", agency.getId());
    }

    /** A/S 요청 + 리뷰까지 생성하는 헬퍼 */
    private Review createReview(User customer, User engineer, Agencies targetAgency,
                                int rating, LocalDateTime createdAt, boolean isVisible) {
        Appliance appliance = applianceRepository.save(
                Appliance.create(customer, leafCategory, "삼성", "AF17B123", null, null, null, null));

        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("테스트 주소")
                .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
        req.processAssignment(targetAgency);
        AsRequest saved = asRequestRepository.save(req);

        asAssignmentRepository.save(AsAssignment.create(saved, engineer, targetAgency, AssignType.MANUAL));

        Review review = Review.create(saved, customer, engineer, rating, "리뷰 내용");
        // isVisible / createdAt 강제 세팅 (H2 테스트 전용)
        ReflectionTestUtils.setField(review, "isVisible", isVisible);
        if (createdAt != null) {
            ReflectionTestUtils.setField(review, "createdAt", createdAt);
        }
        return reviewRepository.save(review);
    }

    private User createCustomer(String email, String name) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed")
                .name(name).phone("010-1111-2222")
                .role(Role.CUSTOMER).build());
    }

    @Nested
    @DisplayName("TC-I-1. 본 대행사 기사의 리뷰만 조회")
    class AgencyIsolation {

        @Test
        void 타대행사_리뷰는_결과에서_제외된다() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, otherEngineerUser, otherAgency, 4, null, true);

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), new AgencyReviewSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).engineerName()).isEqualTo("테스트기사");
        }
    }

    @Nested
    @DisplayName("TC-I-2. rating 필터")
    class RatingFilter {

        @Test
        void rating_5_필터시_5점_리뷰만_반환() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, engineerUser, agency, 3, null, true);

            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "rating", 5);

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).rating()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("TC-I-3. isVisible 필터")
    class VisibilityFilter {

        @Test
        void isVisible_false_필터시_숨겨진_리뷰_제외() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, engineerUser, agency, 4, null, false);

            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "isVisible", true);

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("TC-I-4. keyword 검색")
    class KeywordSearch {

        @Test
        void 고객명으로_부분일치_검색() throws Exception {
            User customer1 = createCustomer("c1@test.com", "김민수");
            User customer2 = createCustomer("c2@test.com", "이철수");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, engineerUser, agency, 4, null, true);

            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "keyword", "김민");

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).customerName()).isEqualTo("김민수");
        }
    }

    @Nested
    @DisplayName("TC-I-5. dateFrom/dateTo 범위 필터")
    class DateRangeFilter {

        @Test
        void 범위_내_리뷰만_포함되고_경계값_포함() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            User customer3 = createCustomer("c3@test.com", "고객3");
            // 포함돼야 할 리뷰: 2024-06-01 ~ 2024-06-30
            createReview(customer1, engineerUser, agency, 5,
                    LocalDateTime.of(2024, 6, 1, 0, 0), true);  // 경계: 포함
            createReview(customer2, engineerUser, agency, 4,
                    LocalDateTime.of(2024, 6, 30, 12, 0), true); // 범위 내
            createReview(customer3, engineerUser, agency, 3,
                    LocalDateTime.of(2024, 7, 1, 0, 0), true);  // 경계 밖: 제외

            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-06-01");
            ReflectionTestUtils.setField(filter, "dateTo", "2024-06-30");

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("TC-I-6. 페이징")
    class Paging {

        @Test
        void 리뷰_11건_size_10이면_1페이지_10건_totalPages_2() throws Exception {
            for (int i = 0; i < 11; i++) {
                User customer = createCustomer("c" + i + "@test.com", "고객" + i);
                createReview(customer, engineerUser, agency, 5, null, true);
            }

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), new AgencyReviewSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(10);
            assertThat(result.totalPages()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(11L);
        }
    }

    @Nested
    @DisplayName("TC-I-7. stats는 필터 무관 전체 모수 기준")
    class StatsTotalScope {

        @Test
        void rating_5_필터시_content_필터링되지만_stats_totalCount는_전체_기준() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, engineerUser, agency, 3, null, true);

            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "rating", 5);

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), filter, PAGEABLE);

            // content는 1건 (rating=5 필터)
            assertThat(result.content()).hasSize(1);
            // stats.totalCount는 전체 2건 기준
            assertThat(result.stats().totalCount()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("TC-I-8. fiveStarRate 정확성")
    class FiveStarRateAccuracy {

        @Test
        void fiveStarRate_비율이_응답에_정확히_반영된다() throws Exception {
            User customer1 = createCustomer("c1@test.com", "고객1");
            User customer2 = createCustomer("c2@test.com", "고객2");
            User customer3 = createCustomer("c3@test.com", "고객3");
            User customer4 = createCustomer("c4@test.com", "고객4");
            createReview(customer1, engineerUser, agency, 5, null, true);
            createReview(customer2, engineerUser, agency, 5, null, true);
            createReview(customer3, engineerUser, agency, 5, null, true);
            createReview(customer4, engineerUser, agency, 3, null, true);

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUserDetails(), new AgencyReviewSearchRequest(), PAGEABLE);

            // 5점 3건 / 전체 4건 = 75.0%
            assertThat(result.stats().fiveStarRate()).isEqualTo(75.0);
        }
    }
}
