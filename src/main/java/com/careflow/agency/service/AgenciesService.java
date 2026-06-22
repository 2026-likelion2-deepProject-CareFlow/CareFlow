package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgenciesService {

    private final AgenciesRepository agenciesRepository;
    private final AccountRequestsRepository accountRequestsRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Agencies findByBusinessNumber(String businessNumber) {
        return agenciesRepository.findByBusinessNumber(businessNumber)
                .orElseThrow(() -> new NoSuchElementException("해당 대행사 정보를 찾을 수 없습니다.")); // 비즈니스 관점 에러처리
    }

    @Transactional
    public Long requestAgencyAccount(AgencyCreateRequest agencyCreateRequest) {

        String businessNumber = agencyCreateRequest.businessNumber();
        Agencies agencies = agenciesRepository.findByBusinessNumber(businessNumber).orElse(null);

        if (agencies != null) {

            if (userRepository.existsByEmail(agencyCreateRequest.email())) {
                throw new IllegalArgumentException("이미 가입된 이메일입니다.");
            }

            // approval_status : APPROVED 인 대행사라면 일반 관리자 계청 요청 생성
            Long accountRequestId = null;
            if (agencies.getApprovalStatus() == AgencyStatus.APPROVED){
                Regions regions = regionRepository.findByName(agencyCreateRequest.regionName()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

                AccountRequests accountRequests = AccountRequests.create(agencies,
                        agencyCreateRequest.email(),
                        passwordEncoder.encode(agencyCreateRequest.password()),
                        agencyCreateRequest.name(),
                        agencyCreateRequest.phoneNumber(),
                        AccountRequestsRole.AGENCY,
                        agencyCreateRequest.agencyAddress(),
                        regions);
                 accountRequestId = accountRequestsRepository.save(accountRequests).getId();

            } else if (agencies.getApprovalStatus() == AgencyStatus.PENDING){
                throw new IllegalStateException("아직 등록 대기중인 대행사 입니다. 등록이 된 이후 다시 요청해주세요.");
            } else if (agencies.getApprovalStatus() == AgencyStatus.REJECTED){
                // AgencyStatus.REJECTED : 대행사에 대해 슈퍼 계정을 요청했으나 거부당한 경우
                // businessNumber 가 unique 제약조건을 가지고 있기 때문에 같은 대행사에 대해 중복으로 슈퍼 계정 생성 요청은 불가능
                throw new IllegalStateException("등록이 거부된 대행사 입니다. 관리자에게 문의해주세요.");
            }
            return accountRequestId;

        } else {
            if (userRepository.existsByEmail(agencyCreateRequest.email())) {
                throw new IllegalArgumentException("이미 가입된 이메일입니다.");
            }
            // 1. 대행사 정보 우선 저장(approval_status : PENDING)
            Long agencyId = agenciesRepository.save(
                    Agencies.create(agencyCreateRequest.agencyName(),
                            agencyCreateRequest.businessNumber(),
                            agencyCreateRequest.agencyAddress(),
                            agencyCreateRequest.agencyFeeRate())).getId();

            agencies = agenciesRepository.findById(agencyId).orElseThrow(() -> new NoSuchElementException("대행사 정보가 저장되지 않았습니다."));

            // 2. 계정 요청 테이블에 저장한 대행사 agency_id 값(Agencies 객체)과 함께 적재
            Regions regions = regionRepository.findByName(agencyCreateRequest.regionName()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

            AccountRequests accountRequests = AccountRequests.create(agencies,
                    agencyCreateRequest.email(),
                    passwordEncoder.encode(agencyCreateRequest.password()),
                    agencyCreateRequest.name(),
                    agencyCreateRequest.phoneNumber(),
                    AccountRequestsRole.AGENCY,
                    agencyCreateRequest.addressDetail(),
                    regions);
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
