package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.dto.request.AgencyFeeRateUpdateRequest;
import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyFeeRateResponse;
import com.careflow.agency.dto.response.AgencyProfileResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
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
    private final AgencyBankAccountRepository agencyBankAccountRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Agencies findByAgencyName(String agencyName) {
        // PENDING·REJECTED 상태의 대행사는 조회 대상에서 제외 — 수리기사 및 대행사 계정 요청 흐름에서 잘못된 대행사 선택 방지
        return agenciesRepository.findByAgencyNameAndApprovalStatus(agencyName, AgencyStatus.APPROVED)
                .orElseThrow(() -> new NoSuchElementException("해당 대행사 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public Long requestAgencyAccount(AgencyCreateRequest agencyCreateRequest) {

        String businessNumber = agencyCreateRequest.businessNumber();
        Agencies agencies = agenciesRepository.findByBusinessNumber(businessNumber).orElse(null);

        if (agencies != null) {

            // users 뿐 아니라 account_requests 에도 동일 이메일이 없어야 함 (unique 제약 선제 검증)
            if (userRepository.existsByEmail(agencyCreateRequest.email())
                    || accountRequestsRepository.existsByEmail(agencyCreateRequest.email())) {
                throw new IllegalArgumentException("이미 가입된 이메일입니다.");
            }

            // approval_status : APPROVED 인 대행사라면 일반 관리자 계청 요청 생성
            Long accountRequestId = null;
            if (agencies.getApprovalStatus() == AgencyStatus.APPROVED){
                Regions regions = regionRepository.findById(agencyCreateRequest.regionId()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

                AccountRequests accountRequests = AccountRequests.create(agencies,
                        agencyCreateRequest.email(),
                        passwordEncoder.encode(agencyCreateRequest.password()),
                        agencyCreateRequest.name(),
                        agencyCreateRequest.phoneNumber(),
                        AccountRequestsRole.AGENCY,
                        agencyCreateRequest.addressDetail(),
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
            // users 뿐 아니라 account_requests 에도 동일 이메일이 없어야 함 (unique 제약 선제 검증)
            if (userRepository.existsByEmail(agencyCreateRequest.email())
                    || accountRequestsRepository.existsByEmail(agencyCreateRequest.email())) {
                throw new IllegalArgumentException("이미 가입된 이메일입니다.");
            }
            // 1. 대행사 정보 우선 저장(approval_status : PENDING)
            // 회원가입 폼은 수수료율을 퍼센트(예: 10.5)로 입력받지만, agencies.agency_fee_rate는
            // 비율(0~1, 예: 0.105)로 저장하므로 100으로 나눠 변환 — 시드 데이터·정산 계산과 단위 통일
            Long agencyId = agenciesRepository.save(
                    Agencies.create(agencyCreateRequest.agencyName(),
                            agencyCreateRequest.businessNumber(),
                            agencyCreateRequest.agencyAddress(),
                            agencyCreateRequest.agencyFeeRate() / 100)).getId();

            agencies = agenciesRepository.findById(agencyId).orElseThrow(() -> new NoSuchElementException("대행사 정보가 저장되지 않았습니다."));

            // 2. 계정 요청 테이블에 저장한 대행사 agency_id 값(Agencies 객체)과 함께 적재
            Regions regions = regionRepository.findById(agencyCreateRequest.regionId()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

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

    // 대행사 내 정보 조회 (설정 페이지용)
    // JWT의 agencyId 기준으로 조회 — 대표 계정뿐 아니라 소속 staff 계정도 자기 대행사 정보를 조회할 수 있어야 함
    @Transactional(readOnly = true)
    public AgencyProfileResponse getProfile(Long agencyId) {
        Agencies agencies = agenciesRepository.findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));
        AgencyBankAccount bankAccount = agencyBankAccountRepository.findByAgencyId(agencyId).orElse(null);
        return AgencyProfileResponse.from(agencies, bankAccount);
    }

    // 대행사 프로필(상호명, 주소, 정산금 수취 계좌) 수정
    @Transactional
    public AgencyProfileResponse updateProfile(Long userId, AgencyProfileUpdateRequest request) {
        // JWT userId 로 대행사 조회 — 존재하지 않으면 404
        Agencies agencies = agenciesRepository.findByRepresentativeById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

        agencies.updateProfile(request.agencyName(), request.agencyAddress());
        AgencyBankAccount bankAccount = upsertBankAccount(agencies, request);
        return AgencyProfileResponse.from(agencies, bankAccount);
    }

    // 정산금 수취 계좌 등록/수정 — bankName·accountNumber 둘 다 제공된 경우에만 반영(선택 입력)
    // 신규 등록 시 account_holder는 프론트에서 아직 입력받지 않아 대행사 상호명으로 기본 설정
    private AgencyBankAccount upsertBankAccount(Agencies agencies, AgencyProfileUpdateRequest request) {
        boolean hasBankInfo = request.bankName() != null && !request.bankName().isBlank()
                && request.accountNumber() != null && !request.accountNumber().isBlank();

        AgencyBankAccount bankAccount = agencyBankAccountRepository.findByAgencyId(agencies.getId()).orElse(null);
        if (!hasBankInfo) {
            return bankAccount;
        }

        if (bankAccount == null) {
            bankAccount = AgencyBankAccount.create(
                    agencies.getId(), request.bankName(), request.accountNumber(), agencies.getAgencyName());
        } else {
            bankAccount.update(request.bankName(), request.accountNumber(), bankAccount.getAccountHolder());
        }
        return agencyBankAccountRepository.save(bankAccount);
    }

    // 대행사 수수료율 조회
    @Transactional(readOnly = true)
    public AgencyFeeRateResponse getFeeRate(Long userId) {
        Agencies agencies = agenciesRepository.findByRepresentativeById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

        return AgencyFeeRateResponse.from(agencies);
    }

    // 대행사 수수료율 수정
    // agencies.agency_fee_rate는 비율(0~1)로 저장됨(v14 스키마) — 요청값을 변환 없이 그대로 저장
    @Transactional
    public AgencyFeeRateResponse updateFeeRate(Long userId, AgencyFeeRateUpdateRequest request) {
        // 수수료율 범위 검증: 0 이상 1 이하 (비율)
        if (request.agencyFeeRate() < 0 || request.agencyFeeRate() > 1) {
            throw new IllegalArgumentException("수수료율은 0 이상 1 이하의 비율이어야 합니다.");
        }

        Agencies agencies = agenciesRepository.findByRepresentativeById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

        agencies.updateFeeRate(request.agencyFeeRate());
        return AgencyFeeRateResponse.from(agencies);
    }
}
