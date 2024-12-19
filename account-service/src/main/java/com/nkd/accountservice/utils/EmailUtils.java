package com.nkd.accountservice.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class EmailUtils {

    public static void handleEmailException(String message) {
        log.error(message);
        throw new RuntimeException(message);
    }

    public static String getRegistrationMessage(String accountName, Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime, String verifyHost) {
        return """
                <html>
                    <body>
                        <h2>Account Activation</h2>
                        <p>Dear %s,</p>
                        <p>Thank you for signing up with us. Please click the link below to activate your account.</p>
                        <a href="%s/verify?uid=%s&confirmid=%s&token=%s">Activate Account</a>
                        <p>This link will expire on %s</p>
                    </body>
                </html>
                """.formatted(accountName, verifyHost, accountID, confirmationID, token, expirationTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
    }
}
