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

    public static String getRegistrationMessage(Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime, String verifyHost) {
        return """
                <html>
                    <body>
                        <h2>Account Activation</h2>
                        <p>Hello,</p>
                        <p>Thank you for signing up with us. Please click the link below to activate your account.</p>
                        <a href="%s/accounts/activate?uid=%s&confirmid=%s&token=%s">Activate Account</a>
                        <p>This link will expire on %s</p>
                    </body>
                </html>
                """.formatted(verifyHost, accountID, confirmationID, token, expirationTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
    }

    public static String getReactivateMessage(Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime, String verifyHost) {
        return """
            <html>
                <body>
                    <h2>Account Reactivation</h2>
                    <p>Hello,</p>
                    <p>We noticed that you requested to reactivate your account. Please click the link below to complete the reactivation process.</p>
                    <a href="%s/accounts/activate?uid=%s&confirmid=%s&token=%s">Reactivate Account</a>
                    <p>This link will expire on %s. If you did not request this, please ignore this email.</p>
                </body>
            </html>
            """.formatted(verifyHost, accountID, confirmationID, token, expirationTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
    }

    public static String getResetPasswordMessage(String code, LocalDateTime expirationTime) {
        return """
        <html>
            <head>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        font-size: 14px;
                        margin: 20px;
                    }
                    h2 {
                        color: #333;
                        font-size: 18px;
                    }
                    p {
                        color: #555;
                        margin: 10px 0;
                    }
                    .code {
                        font-family: monospace;
                        font-size: 16px;
                        font-weight: bold;
                        color: #007bff;
                    }
                </style>
            </head>
            <body>
                <h2>Password Reset</h2>
                <p>Hello,</p>
                <p>We received a request to reset your password.</p>
                <p>Please use the following code to reset your password:</p>
                <p>**Code:** <span class="code">%s</span></p>
                <p>This code will expire on %s. If you did not request this, please ignore this email.</p>
            </body>
        </html>
        """.formatted(code, expirationTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
    }
}
