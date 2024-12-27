package com.nkd.accountservice.service;

import com.nkd.accountservice.security.CustomUserDetails;

public interface JwtService {
    String generateLoginToken(String email);
    boolean validateToken(String token, CustomUserDetails userDetails);
    String extractEmail(String token);
}
