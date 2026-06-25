package com.careflow.as_request.service;

import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgencyAsRequestService {

    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;

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
     * - date 미입력 시 전체 날짜 조회
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

        List<AsRequest> requests = asRequestRepository
                .searchByAgencyFilter(agencyId, AsStatus.COMPLETED, date, filterStatus);

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
