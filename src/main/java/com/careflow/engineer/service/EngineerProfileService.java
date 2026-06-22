package com.careflow.engineer.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.engineer.dto.ProfileResponse;
import com.careflow.engineer.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerProfileService {
    private final EngineerProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ApplianceCategoryRepository categoryRepository;
    private final AgenciesRepository agenciesRepository;
    private final RegionRepository regionRepository;
    private final AccountRequestsRepository accountRequestsRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ProfileResponse updateProfile(Long userId, CreateProfileRequest request){    // 프로필 작성
        User user = userRepository.findById(userId) // 유저 조회
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if(user.getRole() != Role.ENGINEER){    // 수리기사 권한인지
            throw new IllegalArgumentException("수리기사 권한 가진 계정만 프로필을 생성할 수 있습니다.");
        }

        EngineerProfile profile = profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("대행사 승인이 완료되지 않은 기사입니다."));

        if(profile.getCategory() != null && profile.getCareerStartedYear() != null){
            throw new IllegalArgumentException("이미 프로필 등록을 완료한 기사입니다.");
        }

        // 카테고리 조회
        ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(()-> new IllegalArgumentException("전문 분야 정보가 존재하지 않습니다."));

        // 소분류가 맞는지 확인
        if(category.getDepth() != 2) {
            throw new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
        }

        // 연차 및 등급 산정
        SkillLevel calculatedSkillLevel = calculateSkillLevel(request.getCareerStartedYear());

        profile.completeProfile(category, request.getCareerStartedYear(), calculatedSkillLevel, request.getIntroduction());

        EngineerProfile savedProfile = profileRepository.save(profile);

        return ProfileResponse.from(savedProfile);
    }




    @Transactional
    public Long requestEngineerAccount(@Valid EngineerAccountRequest request) {
        String businessNumber = request.businessNumber();
        Agencies agencies = agenciesRepository.findByBusinessNumber(businessNumber).orElseThrow(() -> new NoSuchElementException("입력받은 대행사 정보가 존재하지 않습니다."));

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // approval_status : APPROVED 인 대행사라면 수리기사 계청 요청 생성
        Long accountRequestId = null;
        if (agencies.getApprovalStatus() == AgencyStatus.APPROVED){
            Regions regions = regionRepository.findByName(request.regionName()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));

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
            throw new IllegalStateException("아직 등록 대기중인 수리 기사입니다. 등록이 된 이후 다시 요청해주세요.");
        } else if (agencies.getApprovalStatus() == AgencyStatus.REJECTED){
            // AgencyStatus.REJECTED : 대행사에 대해 슈퍼 계정을 요청했으나 거부당한 경우
            // businessNumber 가 unique 제약조건을 가지고 있기 때문에 같은 대행사에 대해 중복으로 슈퍼 계정 생성 요청은 불가능
            throw new IllegalStateException("등록이 거부된 수리 기사입니다. 관리자에게 문의해주세요.");
        }
        return accountRequestId;

    }

    private SkillLevel calculateSkillLevel(Integer careerStartedYear) { // 연차별 등급 산정
        if(careerStartedYear == null) {
            throw new IllegalArgumentException("경력 시작 연도가 필요합니다.");
        }
        int workYear = LocalDate.now().getYear() - careerStartedYear + 1;

        if (workYear <= 5) {
            return SkillLevel.BEGINNER;
        } else if (workYear <= 10) {
            return SkillLevel.INTERMEDIATE;
        } else {
            return SkillLevel.ADVANCED;
        }
    }
}
