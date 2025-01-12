package com.nkd.event.service;

import com.nkd.event.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;
    private final RedisTemplate<String, String> redisTemplate;

    public String getInternalKey(){
        return redisTemplate.opsForValue().get("internal_jwt");
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public CustomUserDetails buildCustomUserDetails(String token) {
        Claims claims = extractAllClaims(token);

        String privileges = claims.get("privileges") +
                ",ROLE_" + claims.get("role");

        return CustomUserDetails.builder()
                .username(extractClaim(token, Claims::getSubject))
                .rolePrivileges(privileges)
                .build();
    }

    public boolean validateToken(String token) {
        return !extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token).getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }
}
