package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.region.entity.Regions;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgenciesService {

    private final AgenciesRepository agenciesRepository;
    private final AccountRequestsRepository accountRequestsRepository;

    @Transactional(readOnly = true)
    public Agencies findAgencyByNameAndNumber(String agencyName, String businessNumber) {
        return agenciesRepository.findAgenciesByAgencyNameAndBusinessNumber(agencyName, businessNumber)
                .orElseThrow(() -> new NoSuchElementException("해당 대행사 정보를 찾을 수 없습니다.")); // 비즈니스 관점 에러처리
    }

    @Transactional
    public Long agencySuperAccountRequest(AgencyCreateRequest agencyCreateRequest) {

        // 1. 대행사 정보 우선 저장(approval_status : PENDING)
        Long agencyId = agenciesRepository.save(
                Agencies.create(agencyCreateRequest.agencyName(),
                        agencyCreateRequest.businessNumber(),
                        agencyCreateRequest.agencyAddress(),
                        agencyCreateRequest.agencyFeeRate())).getId();

        Agencies agencies = agenciesRepository.findById(agencyId).orElseThrow();

        // Regions 객체 -> 다음주 월요일 병욱님 오시면 요청 테이블에서 regions_id 컬럼 값 어떻게 적재할지 논의
        // 2. 계정 요청 테이블에 저장한 대행사 agency_id 값(Agencies 객체)과 함께 적재
        AccountRequests accountRequests = AccountRequests.create(agencies,
                agencyCreateRequest.email(),
                agencyCreateRequest.password(),
                agencyCreateRequest.name(),
                agencyCreateRequest.phoneNumber(),
                AccountRequestsRole.AGENCY,
                agencyCreateRequest.agencyAddress());

        // 그 이후 요청 객체 저장()
        Long accountRequestId = accountRequestsRepository.save(accountRequests).getId();

        return accountRequestId;
    }

    @Transactional
    public Long agencyManagerAccountRequest(AgencyCreateRequest agencyCreateRequest) {

        // approval_status : APPROVED
        Agencies agency = agenciesRepository.findAgenciesByAgencyNameAndBusinessNumber(agencyCreateRequest.agencyName(), agencyCreateRequest.businessNumber()).orElseThrow();
        // 이미 존재하는 대행사의 id 값이 필요함, 그러면 여기서 상호명, 사업자 등록번호로 다시 Agencies 객체 조회해오고 그걸 이용해서 요청객체 생성
        AccountRequests accountRequests = AccountRequests.create(agency,
                agencyCreateRequest.email(),
                agencyCreateRequest.password(),
                agencyCreateRequest.name(),
                agencyCreateRequest.phoneNumber(),
                AccountRequestsRole.AGENCY,
                agencyCreateRequest.agencyAddress());

        return accountRequestsRepository.save(accountRequests).getId();
    }

    @Transactional(readOnly = true)
    public Long findRepresentativeIdById(Long agencyId) {

        return agenciesRepository.findRepresentativeIdById(agencyId).orElseThrow();
    }
}
