package com.careflow.engineer.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class EngineerService {

    private final AgenciesRepository agenciesRepository;
    private final UserRepository userRepository;
    private final AccountRequestsRepository accountRequestsRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long requestEngineerAccount(@Valid EngineerAccountRequest request) {
        String businessNumber = request.businessNumber();
        Agencies agencies = agenciesRepository.findByBusinessNumber(businessNumber).orElseThrow(() -> new NoSuchElementException("입력받은 대행사 정보가 존재하지 않습니다."));

        // users 뿐 아니라 account_requests 에도 동일 이메일이 없어야 함 (unique 제약 선제 검증)
        if (userRepository.existsByEmail(request.email())
                || accountRequestsRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // approval_status : APPROVED 인 대행사라면 수리기사 계청 요청 생성
        Long accountRequestId = null;
        if (agencies.getApprovalStatus() == AgencyStatus.APPROVED){
            Regions regions = regionRepository.findById(request.regionId()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

            AccountRequests accountRequests = AccountRequests.create(agencies,
                    request.email(),
                    passwordEncoder.encode(request.password()),
                    request.name(),
                    request.phoneNumber(),
                    AccountRequestsRole.ENGINEER,
                    request.addressDetail(),
                    regions);
            accountRequestId = accountRequestsRepository.save(accountRequests).getId();

        } else if (agencies.getApprovalStatus() == AgencyStatus.PENDING){
            throw new IllegalStateException("아직 등록 대기중인 대행사입니다. 등록이 승인된 이후 다시 요청해주세요");
        } else if (agencies.getApprovalStatus() == AgencyStatus.REJECTED){
            // businessNumber 가 unique 제약조건을 가지고 있기 때문에 같은 대행사에 대해 중복으로 슈퍼 계정 생성 요청은 불가능
            throw new IllegalStateException("등록이 거부된 대행사입니다. 관리자에게 문의해주세요.");
        }
        return accountRequestId;

    }
}
