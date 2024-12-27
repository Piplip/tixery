package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.security.CustomUserDetails;
import com.nkd.accountservice.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.nkd.accountservice.Tables.*;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final DSLContext context;
    private final String secretKey = Jwts.SIG.HS512.key().toString();

    @Override
    public String generateLoginToken(String email) {
        Map<String, Object> claims = new HashMap<>();

        var userData = context.select(USER_DATA.FULL_NAME, USER_DATA.GENDER, USER_DATA.NATIONALITY, USER_DATA.DATE_OF_BIRTH, USER_DATA.PHONE_NUMBER,
                PROFILE.PROFILE_NAME, PROFILE.DESCRIPTION, PROFILE.PROFILE_IMAGE_URL, ROLE.ROLE_PRIVILEGES, ROLE.ROLE_NAME)
                .from(USER_ACCOUNT.join(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                        .join(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID))
                        .join(ROLE).on(ROLE.ROLE_ID.eq(USER_ACCOUNT.ROLE_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchOptional();

        if(userData.isEmpty()){
            return "";
        }

        var record = userData.get();
        claims.put("fullName", record.get(USER_DATA.FULL_NAME));
        claims.put("gender", record.get(USER_DATA.GENDER));
        claims.put("nationality", record.get(USER_DATA.NATIONALITY));
        claims.put("dateOfBirth", record.get(USER_DATA.DATE_OF_BIRTH).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        claims.put("phoneNumber", record.get(USER_DATA.PHONE_NUMBER));
        claims.put("profileName", record.get(PROFILE.PROFILE_NAME));
        claims.put("description", record.get(PROFILE.DESCRIPTION));
        claims.put("profileImageUrl", record.get(PROFILE.PROFILE_IMAGE_URL));
        claims.put("privileges", record.get(ROLE.ROLE_PRIVILEGES));
        claims.put("role", record.get(ROLE.ROLE_NAME));

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(email)
                .issuedAt(new java.util.Date(System.currentTimeMillis()))
                .expiration(new java.util.Date(System.currentTimeMillis() + 1000 * 60 * 60 * 30))
                .and()
                .signWith(getKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token, CustomUserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new java.util.Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    @Override
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
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
