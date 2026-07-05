package com.careflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    // 기존 호출부(테스트 등) 호환용 — isRepresentative 미지정 시 null(AGENCY 외 역할과 동일하게 취급)
    public String generateAccessToken(Long userId, String email, String role, Long agencyId) {
        return generateAccessToken(userId, email, role, agencyId, null);
    }

    public String generateAccessToken(Long userId, String email, String role, Long agencyId, Boolean isRepresentative) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("agencyId", agencyId)
                .claim("isRepresentative", isRepresentative)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try { getClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public Long getUserId(String token) { return Long.parseLong(getClaims(token).getSubject()); }
    public String getEmail(String token) { return getClaims(token).get("email", String.class); }
    public String getRole(String token) { return getClaims(token).get("role", String.class); }
    public Long getAgencyId(String token) { return getClaims(token).get("agencyId", Long.class); }
    public Boolean getIsRepresentative(String token) { return getClaims(token).get("isRepresentative", Boolean.class); }
    public long getAccessTokenExpiration() { return accessTokenExpiration; }
    public long getRefreshTokenExpiration() { return refreshTokenExpiration; }
}