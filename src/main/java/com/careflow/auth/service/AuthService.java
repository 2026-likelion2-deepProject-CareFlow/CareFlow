package com.careflow.auth.service;

import com.careflow.auth.dto.LoginRequest;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }


        if (user.getRole() == Role.AGENCY) {
            // 호준님 여기다가 대행사 승인 상태 검증 로직 구현하시면 됩니다!
        }

        return "JWT_TOKEN_SAMPLE_BY_HYEMIN";
    }
}