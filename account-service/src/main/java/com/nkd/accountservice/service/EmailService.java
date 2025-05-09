package com.nkd.accountservice.service;

import java.time.LocalDateTime;

public interface EmailService {
    void sendRegistrationEmail(String email, Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime);
    void sendActivationEmail(String email, Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime);
    void sendPasswordResetEmail(String email, String code, LocalDateTime expirationTime);
    void sendOAuth2SetPasswordEmail(String email);
}
