package com.careflow.auth.security;

import com.careflow.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        // 블랙리스트에 등록된 토큰(로그아웃된 access token)은 인증 거부
        if (token != null && jwtProvider.validateToken(token)
                && !Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.BLACKLIST_KEY_PREFIX + token))) {
            Long userId = jwtProvider.getUserId(token);
            String email = jwtProvider.getEmail(token);
            String role = jwtProvider.getRole(token);
            Long agencyId = jwtProvider.getAgencyId(token);
            Boolean isRepresentative = jwtProvider.getIsRepresentative(token);

            CustomUserDetails userDetails = new CustomUserDetails(userId, email, null, role, agencyId, isRepresentative);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}