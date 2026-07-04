package com.careflow.auth.service;

import com.careflow.auth.dto.PasswordResetRequest;
import com.careflow.auth.dto.PasswordSendCodeRequest;
import com.careflow.auth.dto.PasswordVerifyCodeRequest;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String REDIS_PREFIX = "pwd:reset:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public void sendCode(PasswordSendCodeRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("가입된 이메일이 아닙니다."));

        // OAuth2 전용 계정(비밀번호 없는 계정)은 비밀번호 찾기 불가
        if (user.getPasswordHash() == null) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호 찾기를 사용할 수 없습니다.");
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(REDIS_PREFIX + request.email(), code, CODE_TTL);

        sendEmail(request.email(), code);
    }

    public void verifyCode(PasswordVerifyCodeRequest request) {
        validateCode(request.email(), request.code());
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        validateCode(request.email(), request.code());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("가입된 이메일이 아닙니다."));

        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        // 사용된 코드 즉시 삭제
        redisTemplate.delete(REDIS_PREFIX + request.email());
    }

    private void validateCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(REDIS_PREFIX + email);
        if (stored == null) {
            throw new IllegalArgumentException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!stored.equals(code)) {
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");
        }
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(code);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[CareFlow] 비밀번호 재설정 인증 코드");
        message.setText(
                "안녕하세요, CareFlow입니다.\n\n" +
                "비밀번호 재설정을 위한 인증 코드를 안내드립니다.\n\n" +
                "인증 코드: " + code + "\n\n" +
                "이 코드는 5분간 유효합니다.\n" +
                "본인이 요청하지 않은 경우 이 메일을 무시하세요."
        );
        mailSender.send(message);
    }
}
