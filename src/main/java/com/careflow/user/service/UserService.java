package com.careflow.user.service;

import com.careflow.user.dto.UserAddressResponse;
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
}
