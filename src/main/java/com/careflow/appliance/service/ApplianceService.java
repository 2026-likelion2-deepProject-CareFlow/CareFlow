package com.careflow.appliance.service;

import com.careflow.appliance.dto.ApplianceCreateRequest;
import com.careflow.appliance.dto.ApplianceResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplianceService {

    private final ApplianceRepository applianceRepository;
    private final UserRepository userRepository;
    private final ApplianceCategoryRepository categoryRepository;

    /**
     * 가전제품 등록
     * 1) 사용자 존재 여부 확인
     * 2) 카테고리 존재 여부 확인 (소분류 depth=2만 허용)
     * 3) Appliance 엔티티 생성 후 저장
     */
    @Transactional
    public ApplianceResponse registerAppliance(Long userId, ApplianceCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        // 카테고리 검증: 소분류(depth=2)만 가전 카테고리로 허용
        ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전 카테고리입니다."));
        if (category.getDepth() != 2) {
            throw new IllegalArgumentException("가전 카테고리는 소분류(depth=2)만 선택 가능합니다.");
        }

        Appliance appliance = Appliance.create(
                user,
                category,
                request.getBrand(),
                request.getModelName(),
                request.getSerialNumber(),
                request.getPurchaseDate(),
                request.getWarrantyEndDate(),
                request.getRegisterMethod()
        );

        return ApplianceResponse.from(applianceRepository.save(appliance));
    }

    /**
     * 내 가전제품 목록 조회 (논리 삭제된 항목 제외, 최신순)
     */
    public List<ApplianceResponse> getMyAppliances(Long userId) {
        return applianceRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(ApplianceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 가전제품 상세 조회
     * 본인 소유 가전인지 확인 후 반환
     */
    public ApplianceResponse getApplianceDetail(Long userId, Long applianceId) throws IllegalAccessException {
        Appliance appliance = applianceRepository.findByIdAndDeletedAtIsNull(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if (!appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품만 조회할 수 있습니다.");
        }

        return ApplianceResponse.from(appliance);
    }

    /**
     * 가전제품 논리 삭제
     * 본인 소유 가전인지 확인 후 deletedAt 세팅
     */
    @Transactional
    public void deleteAppliance(Long userId, Long applianceId) throws IllegalAccessException {
        Appliance appliance = applianceRepository.findByIdAndDeletedAtIsNull(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if (!appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품만 삭제할 수 있습니다.");
        }

        appliance.delete();
    }
}
