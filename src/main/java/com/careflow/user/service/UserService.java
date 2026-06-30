package com.careflow.user.service;

import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.dto.UserAddressResponse;
import com.careflow.user.dto.UserProfileResponse;
import com.careflow.user.dto.UserUpdateRequest;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    public Long saveUser(User user) {
        return userRepository.save(user).getId();
    }

    public User findById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * 고객 주소 정보 조회
     * region이 null이면 regionId·regionName도 null로 반환
     */
    @Transactional(readOnly = true)
    public UserAddressResponse getMyAddress(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));
        return UserAddressResponse.from(user);
    }

    /**
     * 로그인한 사용자 본인 프로필 조회
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));
        return UserProfileResponse.from(user);
    }

    /**
     * 로그인한 사용자 본인 정보 수정
     * regionId가 있으면 Regions 엔티티를 조회해서 전달, null이면 기존 지역 유지
     */
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        Regions region = null;
        if (request.regionId() != null) {
            region = regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 지역입니다."));
        }

        user.updateProfile(request.name(), request.phone(), region, request.addressDetail());
        return UserProfileResponse.from(user);
    }
}
