package com.nkd.accountservice.service;

import com.nkd.accountservice.security.CustomUserDetails;

public interface JwtService {
    String generateLoginToken(String email);
    String generateOauth2LoginToken(String email, String name, String picture);
    boolean validateToken(String token, CustomUserDetails userDetails);
    String extractEmail(String token);
    boolean isTokenExpired(String token);
    String extendSession(String token);
}
