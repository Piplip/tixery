package com.nkd.accountservice.service;

import java.time.LocalDateTime;

public interface EmailService {
    void sendRegistrationEmail(String email, Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime);
}
