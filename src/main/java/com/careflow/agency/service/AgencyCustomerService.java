package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyCustomerSearchRequest;
import com.careflow.agency.dto.response.AgencyCustomerApplianceResponse;
import com.careflow.agency.dto.response.AgencyCustomerAsRequestResponse;
import com.careflow.agency.dto.response.AgencyCustomerListResponse;
import com.careflow.agency.dto.response.AgencyCustomerPaymentResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyCustomerService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;
    private final ApplianceRepository applianceRepository;
    private final AsRequestRepository asRequestRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter JOINED_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 대행사 소속 기사에게 COMPLETED 서비스를 1회 이상 받은 고객 목록 조회 (페이징)
     * - stats: 검색 필터와 무관하게 항상 전체 모수(COMPLETED 고객 전체) 기준 집계
     * - content: keyword/status/joinedFrom/joinedTo 필터를 적용한 페이징 결과
     */
    @Transactional(readOnly = true)
    public AgencyCustomerListResponse searchCustomers(
            CustomUserDetails userDetails, Pageable pageable, AgencyCustomerSearchRequest request)
            throws IllegalAccessException {

        // ENGINEER 역할도 agencyId 클레임을 보유하므로 명시적으로 AGENCY만 허용
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        List<Long> customerIds =
                asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(userDetails.getAgencyId());

        if (customerIds.isEmpty()) {
            return AgencyCustomerListResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        AgencyCustomerListResponse.Stats stats = buildStats(customerIds);

        String keyword = blankToNull(request != null ? request.keyword() : null);
        String status = blankToNull(request != null ? request.status() : null);
        // grade, joinPath는 users 테이블에 대응 컬럼이 없어(DB 미지원) 검색 조건으로 사용하지 않는다.
        LocalDateTime joinedFrom = parseJoinedDate(request != null ? request.joinedFrom() : null, false);
        LocalDateTime joinedTo = parseJoinedDate(request != null ? request.joinedTo() : null, true);

        Page<User> page = userRepository.searchAgencyCustomers(
                customerIds, status, keyword, joinedFrom, joinedTo, pageable);

        Map<Long, Long> applianceCountMap = buildApplianceCountMap(page.getContent());

        return AgencyCustomerListResponse.of(stats, page, applianceCountMap);
    }

    /**
     * 대행사 소속 기사에게 COMPLETED 서비스를 받은 특정 고객의 가전 목록 조회
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @Transactional(readOnly = true)
    public List<AgencyCustomerApplianceResponse> getCustomerAppliances(
            CustomUserDetails userDetails, Long userId) throws IllegalAccessException {

        verifyAgencyAccessToCustomer(userDetails, userId);

        List<Appliance> appliances = applianceRepository.findByUserIdWithCategory(userId);
        return appliances.stream().map(AgencyCustomerApplianceResponse::from).toList();
    }

    /**
     * 대행사 소속 기사에게 COMPLETED 서비스를 받은 특정 고객이 본인 대행사로 접수한 A/S 이력 조회
     * - 상태 필터 없이 전체 이력(취소/완료 포함) 반환, 타 대행사로 접수한 이력은 제외
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @Transactional(readOnly = true)
    public List<AgencyCustomerAsRequestResponse> getCustomerAsRequests(
            CustomUserDetails userDetails, Long userId) throws IllegalAccessException {

        verifyAgencyAccessToCustomer(userDetails, userId);

        return asRequestRepository.findByCustomerIdAndAgencyId(userId, userDetails.getAgencyId())
                .stream().map(AgencyCustomerAsRequestResponse::from).toList();
    }

    /**
     * 대행사 소속 기사에게 COMPLETED 서비스를 받은 특정 고객이 본인 대행사로 접수한 A/S 건의 결제 내역 조회
     * - 상태 필터 없이 전체 결제 이력(READY/FAILED/CANCELLED/REFUNDED 포함) 반환, 타 대행사 접수 건은 제외
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @Transactional(readOnly = true)
    public List<AgencyCustomerPaymentResponse> getCustomerPayments(
            CustomUserDetails userDetails, Long userId) throws IllegalAccessException {

        verifyAgencyAccessToCustomer(userDetails, userId);

        return paymentRepository.findByCustomerIdAndAgencyId(userId, userDetails.getAgencyId())
                .stream().map(AgencyCustomerPaymentResponse::from).toList();
    }

    /**
     * 고객 차단 — status를 SUSPENDED로 전환. 로그인 시점에 AuthService.login()이 이미
     * "ACTIVE가 아니면 로그인 거부"를 검증하고 있으므로 이 전환만으로 즉시 차단이 적용된다.
     * 이미 SUSPENDED인 경우도 멱등하게 처리(중복 클릭 방어).
     */
    @Transactional
    public void blockCustomer(CustomUserDetails userDetails, Long userId) throws IllegalAccessException {
        verifyAgencyAccessToCustomer(userDetails, userId);

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다."));

        customer.updateStatus("SUSPENDED");
    }

    /**
     * 고객 차단 해제 — status를 ACTIVE로 복원
     */
    @Transactional
    public void unblockCustomer(CustomUserDetails userDetails, Long userId) throws IllegalAccessException {
        verifyAgencyAccessToCustomer(userDetails, userId);

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다."));

        customer.updateStatus("ACTIVE");
    }

    // 고객 상세 화면(가전/A·S이력 등) 공통 인가 검증 — role=AGENCY, 유저 존재, 본인 대행사 COMPLETED 서비스 이력 보유
    private void verifyAgencyAccessToCustomer(CustomUserDetails userDetails, Long userId)
            throws IllegalAccessException {

        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다.");
        }

        List<Long> customerIds =
                asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(userDetails.getAgencyId());
        if (!customerIds.contains(userId)) {
            throw new IllegalAccessException("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }
    }

    // 이번 달/저번 달 신규 가입 고객 수 비교는 createdAt 기준 [월초, 다음달 월초) 범위로 산정
    private AgencyCustomerListResponse.Stats buildStats(List<Long> customerIds) {
        long totalCount = userRepository.countByIdInAndRole(customerIds, Role.CUSTOMER);
        long activeCount = userRepository.countByIdInAndStatus(customerIds, "ACTIVE");
        long inactiveCount = userRepository.countByIdInAndStatus(customerIds, "INACTIVE");

        LocalDateTime startOfThisMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfThisMonth.plusMonths(1);
        LocalDateTime startOfPrevMonth = startOfThisMonth.minusMonths(1);

        long newThisMonth = userRepository.countByIdInAndCreatedAtRange(
                customerIds, startOfThisMonth, startOfNextMonth);
        long prevMonthDiff = userRepository.countByIdInAndCreatedAtRange(
                customerIds, startOfPrevMonth, startOfThisMonth);
        long newThisMonthDiff = newThisMonth - prevMonthDiff;

        return new AgencyCustomerListResponse.Stats(
                totalCount, activeCount, inactiveCount, newThisMonth, prevMonthDiff, newThisMonthDiff);
    }

    // 페이지에 담긴 고객들의 가전 보유 개수를 한 번에 조회해 Map으로 변환 (N+1 방지)
    private Map<Long, Long> buildApplianceCountMap(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        return applianceRepository.countActiveByUserIds(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    // joinedFrom: 해당 날짜 00:00:00 이상, joinedTo: 해당 날짜 다음날 00:00:00 미만(해당일 포함)
    private LocalDateTime parseJoinedDate(String dateStr, boolean isUpperBound) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, JOINED_DATE_FORMATTER);
            return isUpperBound ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("가입일 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }
    }
}
