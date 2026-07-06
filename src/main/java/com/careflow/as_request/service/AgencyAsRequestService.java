package com.careflow.as_request.service;

import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.dto.AgencyDashboardSummaryResponse;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.Role;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgencyAsRequestService {

    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 대행사 소속 A/S 요청 전체 목록 조회.
     * 수리 완료(COMPLETED) 요청은 제외한다.
     * 1. AGENCY 권한 확인
     * 2. 로그인 유저의 소속 agency_id 추출
     * 3. as_requests 에서 agency_id 일치 + COMPLETED 제외 조건으로 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AgencyAsRequestListResponse> getAsRequestsByAgency(
            CustomUserDetails userDetails) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        List<AsRequest> requests = asRequestRepository
                .findByAgencyIdExcludeStatus(agencyId, AsStatus.COMPLETED);

        return requests.stream()
                .map(AgencyAsRequestListResponse::from)
                .toList();
    }

    /**
     * 대행사 소속 A/S 요청 필터링 조회.
     * - date(접수일, created_at 기준) 미입력 시 전체 날짜 조회
     * - status 미입력 시 전체 상태 조회
     * - status = COMPLETED 전달 시 빈 리스트 반환 (수리 완료 요청은 이 API 범위 밖)
     */
    @Transactional(readOnly = true)
    public List<AgencyAsRequestListResponse> searchAsRequests(
            CustomUserDetails userDetails,
            LocalDate date,
            String statusStr) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        // COMPLETED 필터 요청은 이 API 범위에서 항상 빈 결과 반환
        AsStatus filterStatus = parseStatus(statusStr);
        if (filterStatus == AsStatus.COMPLETED) {
            return List.of();
        }

        // 날짜 입력 시 해당 날짜 하루 범위(00:00:00 이상, 다음날 00:00:00 미만)로 변환
        // H2·MySQL 양쪽 호환을 위해 범위 비교 방식 사용
        LocalDateTime startOfDay = (date != null) ? date.atStartOfDay() : null;
        LocalDateTime endOfDay   = (date != null) ? date.plusDays(1).atStartOfDay() : null;

        List<AsRequest> requests = asRequestRepository
                .searchByAgencyFilter(agencyId, AsStatus.COMPLETED, startOfDay, endOfDay, filterStatus);

        return requests.stream()
                .map(AgencyAsRequestListResponse::from)
                .toList();
    }

    /**
     * A/S 요청 단건 상세 조회.
     * 현재 로그인한 대행사 소속의 요청인지 소유권 검증 후 상세 정보를 반환한다.
     */
    @Transactional(readOnly = true)
    public AgencyAsRequestDetailResponse getAsRequestDetail(
            CustomUserDetails userDetails, Long requestId) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        // appliance 포함 JOIN FETCH 단건 조회
        AsRequest request = asRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new NoSuchElementException("해당 A/S 요청을 찾을 수 없습니다. (requestId: " + requestId + ")"));

        // 해당 요청이 현재 대행사 소속인지 검증 — null이면 아직 대행사 미배정 상태
        if (request.getAgency() == null || !request.getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("본인 소속 대행사의 A/S 요청만 조회할 수 있습니다.");
        }

        return AgencyAsRequestDetailResponse.from(request);
    }

    /**
     * 대행사 대시보드 요약 통계 조회.
     * 1. 대행사 전체 누적 A/S 요청 건수
     * 2. 오늘 신규 접수 건수(created_at 기준)
     * 3. 오늘 접수 중 ASSIGNED(기사 배정 대기) 건수
     * 4. 오늘 접수 중 ACCEPTED(기사 배정 승인) 건수
     * 5. 오늘 접수 중 CANCELLED(고객 취소) 건수
     */
    @Transactional(readOnly = true)
    public AgencyDashboardSummaryResponse getDashboardSummary(
            CustomUserDetails userDetails) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        // 오늘 하루 범위 — created_at >= 오늘 00:00:00 AND < 내일 00:00:00
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = startOfDay.plusDays(1);

        long totalCount = asRequestRepository.countByAgencyId(agencyId);

        // status=null → 오늘 신규 접수 전체 건수
        long todayNewCount = asRequestRepository
                .countByAgencyIdAndCreatedDateAndStatus(agencyId, startOfDay, endOfDay, null);

        long todayAssignedCount = asRequestRepository
                .countByAgencyIdAndCreatedDateAndStatus(agencyId, startOfDay, endOfDay, AsStatus.ASSIGNED);

        long todayAcceptedCount = asRequestRepository
                .countByAgencyIdAndCreatedDateAndStatus(agencyId, startOfDay, endOfDay, AsStatus.ACCEPTED);

        long todayCancelledCount = asRequestRepository
                .countByAgencyIdAndCreatedDateAndStatus(agencyId, startOfDay, endOfDay, AsStatus.CANCELLED);

        return new AgencyDashboardSummaryResponse(
                totalCount,
                todayNewCount,
                todayAssignedCount,
                todayAcceptedCount,
                todayCancelledCount
        );
    }

    /**
     * 대행사용: A/S 접수 반려.
     * - 배정 대기(AGENCY_RECEIVED) 상태뿐 아니라, 기사가 아직 수락하지 않은 ASSIGNED 상태
     *   (자동 배정 후 30분 미수락 등)에서도 반려할 수 있어야 하므로 별도의 상태 사전 검증 없이
     *   AsRequest.cancel()에 위임한다 — cancel()이 PENDING/AGENCY_RECEIVED/ASSIGNED만 허용하고
     *   그 외(ACCEPTED 이후 확정 건)는 IllegalStateException을 던진다.
     *   PENDING은 agency가 아직 null이라 아래 소속 검증에서 자연히 걸러진다.
     * - AsRequest.cancel()을 재사용해 CANCELLED 상태로 전환 + cancel_reason 저장
     *   (as_requests.status ENUM에는 별도의 REJECTED 값이 없어 CANCELLED로 통일)
     * - 반려 후 고객에게 재신청 안내 알림 발송. 자동/수동 배정 여부에 따라 문구가 다름
     *   (자동 배정: 30분 미수락/거절로 인한 반려, 수동 배정: 지정 기사가 수락하지 않아 반려)
     */
    @Transactional
    public void rejectAsRequest(CustomUserDetails userDetails, Long requestId, String reason)
            throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("해당 A/S 요청을 찾을 수 없습니다. (requestId: " + requestId + ")"));

        if (asRequest.getAgency() == null || !asRequest.getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("본인 소속 대행사의 A/S 요청만 반려할 수 있습니다.");
        }

        String cancelReason = (reason != null && !reason.isBlank()) ? reason : "대행사 반려";
        asRequest.cancel(cancelReason);

        // 고객 알림 — 자동 배정은 "30분 미수락/거절" 문구, 수동 배정은 "지정 기사 미수락" 문구로 구분
        String title = "A/S 접수 반려 안내";
        String body = (asRequest.getAssignType() == AssignType.AUTO)
                ? "30분 동안 수리기사가 수락하지 않았거나 거부하여 요청이 반려되었습니다. 다시 요청을 시도해주세요."
                : "선택하신 수리기사가 배정을 수락하지 않아 요청이 반려되었습니다. 다시 요청을 시도해주세요.";

        Notification notification = Notification.createAsStatusNotification(asRequest.getCustomer(), title, body);
        notificationRepository.save(notification);
    }

    /**
     * 공통: AGENCY 권한 확인 후 소속 agency_id 추출
     */
    private Long extractAgencyId(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new NoSuchElementException("로그인 정보에 해당하는 사용자를 찾을 수 없습니다."));

        if (user.getAgency() == null) {
            throw new IllegalStateException("소속 대행사 정보가 없습니다.");
        }

        return user.getAgency().getId();
    }

    /**
     * 공통: status 문자열을 AsStatus 로 변환 — null 또는 빈 문자열은 null 반환
     * 유효하지 않은 값이면 IllegalArgumentException 발생
     */
    private AsStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return AsStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 상태 값입니다: " + statusStr);
        }
    }
}
