package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.region.entity.Regions;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgenciesService {

    private final AgenciesRepository agenciesRepository;
    private final AccountRequestsRepository accountRequestsRepository;

    @Transactional(readOnly = true)
    public Agencies findByBusinessNumber(String businessNumber) {
        return agenciesRepository.findByBusinessNumber(businessNumber)
                .orElseThrow(() -> new NoSuchElementException("해당 대행사 정보를 찾을 수 없습니다.")); // 비즈니스 관점 에러처리
    }

    @Transactional
    public Long requestAgencyAccount(AgencyCreateRequest agencyCreateRequest) {

        String agecyName = agencyCreateRequest.agencyName();
        String businessNumber = agencyCreateRequest.businessNumber();
        Agencies agencies = agenciesRepository.findByBusinessNumber(businessNumber).orElse(null);

        if (agencies != null) {
            // approval_status : APPROVED 인 대행사라면 일반 관리자 계청 요청 생성
            Long accountRequestId = null;
            if (agencies.getApprovalStatus() == AgencyStatus.APPROVED){
                AccountRequests accountRequests = AccountRequests.create(agencies,
                        agencyCreateRequest.email(),
                        agencyCreateRequest.password(),
                        agencyCreateRequest.name(),
                        agencyCreateRequest.phoneNumber(),
                        AccountRequestsRole.AGENCY,
                        agencyCreateRequest.agencyAddress(),
                        agencyCreateRequest.regionId());
                 accountRequestId = accountRequestsRepository.save(accountRequests).getId();

            } else if (agencies.getApprovalStatus() == AgencyStatus.PENDING || agencies.getApprovalStatus() == AgencyStatus.REJECTED){
                throw new IllegalStateException("아직 등록 대기중이거나 등록이 거부된 대행사 입니다.");
            }
            return accountRequestId;

        } else {
            // 1. 대행사 정보 우선 저장(approval_status : PENDING)
            Long agencyId = agenciesRepository.save(
                    Agencies.create(agencyCreateRequest.agencyName(),
                            agencyCreateRequest.businessNumber(),
                            agencyCreateRequest.agencyAddress(),
                            agencyCreateRequest.agencyFeeRate())).getId();

            agencies = agenciesRepository.findById(agencyId).orElseThrow(() -> new NoSuchElementException("대행사 정보가 저장되지 않았습니다."));

            // Regions 객체 -> 다음주 월요일 병욱님 오시면 요청 테이블에서 regions_id 컬럼 값 어떻게 적재할지 논의
            // 2. 계정 요청 테이블에 저장한 대행사 agency_id 값(Agencies 객체)과 함께 적재
            AccountRequests accountRequests = AccountRequests.create(agencies,
                    agencyCreateRequest.email(),
                    agencyCreateRequest.password(),
                    agencyCreateRequest.name(),
                    agencyCreateRequest.phoneNumber(),
                    AccountRequestsRole.AGENCY,
                    agencyCreateRequest.addressDetail(),
                    agencyCreateRequest.regionId());

            // 그 이후 요청 객체 저장()
            Long accountRequestId = accountRequestsRepository.save(accountRequests).getId();

            return accountRequestId;
        }
    }

    @Transactional(readOnly = true)
    public Agencies findRepresentativeIdById(Long userId) {

        return agenciesRepository.findByRepresentativeById(userId).orElse(null);
    }
}
