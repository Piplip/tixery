package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.enums.RoleRoleName;
import com.nkd.accountservice.security.CustomUserDetails;
import com.nkd.accountservice.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.nkd.accountservice.Tables.*;

@Service
public class JwtServiceImpl implements JwtService {

    @Autowired
    private DSLContext context;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.secret}")
    private String secretKey;
    private final long EXPIRE_DURATION = TimeUnit.DAYS.toMillis(1);

    @Override
    public String getInternalKey(){
        return redisTemplate.opsForValue().get("internal_jwt");
    }

    @Override
    public String generateLoginToken(String email) {
        Map<String, Object> claims = new HashMap<>();

        var userData = context.select(USER_DATA.USER_DATA_ID, USER_DATA.FULL_NAME, USER_DATA.GENDER, USER_DATA.NATIONALITY, USER_DATA.DATE_OF_BIRTH, USER_DATA.PHONE_NUMBER,
                USER_DATA.INTERESTS, PROFILE.PROFILE_NAME, PROFILE.DESCRIPTION, PROFILE.PROFILE_IMAGE_URL, PROFILE.PROFILE_ID, USER_ACCOUNT.ACCOUNT_ID
                        , ROLE.ROLE_PRIVILEGES, ROLE.ROLE_NAME)
                .from(USER_ACCOUNT.join(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                        .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID))
                        .leftJoin(ROLE).on(ROLE.ROLE_ID.eq(USER_ACCOUNT.ROLE_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchOptional();

        if(userData.isEmpty()){
            return "";
        }

        var record = userData.get();
        Function<Object, String> getValueOrDefault = value -> value == null ? "" : value.toString();
        claims.put("userID", getValueOrDefault.apply(record.get(USER_ACCOUNT.ACCOUNT_ID)));
        claims.put("userDataID", getValueOrDefault.apply(record.get(USER_DATA.USER_DATA_ID)));
        claims.put("fullName", getValueOrDefault.apply(record.get(USER_DATA.FULL_NAME)));
        claims.put("gender", getValueOrDefault.apply(record.get(USER_DATA.GENDER)));
        claims.put("nationality", getValueOrDefault.apply(record.get(USER_DATA.NATIONALITY)));
        claims.put("dateOfBirth", record.get(USER_DATA.DATE_OF_BIRTH) == null ? "" : record.get(USER_DATA.DATE_OF_BIRTH).format(DateTimeFormatter.ISO_DATE));
        claims.put("phoneNumber", getValueOrDefault.apply(record.get(USER_DATA.PHONE_NUMBER)));
        claims.put("profileName", getValueOrDefault.apply(record.get(PROFILE.PROFILE_NAME)));
        claims.put("description", getValueOrDefault.apply(record.get(PROFILE.DESCRIPTION)));
        claims.put("profileImageUrl", getValueOrDefault.apply(record.get(PROFILE.PROFILE_IMAGE_URL)));
        claims.put("privileges", getValueOrDefault.apply(record.get(ROLE.ROLE_PRIVILEGES)));
        claims.put("role", getValueOrDefault.apply(record.get(ROLE.ROLE_NAME)));
        claims.put("profileID", getValueOrDefault.apply(record.get(PROFILE.PROFILE_ID)));
        if (record.get(ROLE.ROLE_NAME) == RoleRoleName.ATTENDEE) {
            claims.put("interests", getValueOrDefault.apply(record.get(USER_DATA.INTERESTS)));
        } else if (record.get(ROLE.ROLE_NAME) == RoleRoleName.HOST) {
            claims.put("organization", getValueOrDefault.apply(record.get(PROFILE.PROFILE_NAME)));
        }

        return buildToken(claims, email, new Date(), new Date(System.currentTimeMillis() + EXPIRE_DURATION));
    }

    public String generateOauth2LoginToken(String email, String name, String picture) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("fullName", name);
        claims.put("profileImageUrl", picture);
        return buildToken(claims, email, new Date(), new Date(System.currentTimeMillis() + EXPIRE_DURATION));
    }

    private String buildToken(Map<String, Object> claims, String subject, Date issuedAt, Date expiration) {
        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .and().signWith(getKey()).compact();
    }

    @Override
    public boolean validateToken(String token, CustomUserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new java.util.Date());
    }

    @Override
    public String generateInternalToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        Date expiredDate = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650));

        return buildToken(claims, email, new Date(), expiredDate);
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
