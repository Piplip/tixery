package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.service.EmailService;
import com.nkd.accountservice.utils.EmailUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String NEW_ACCOUNT_VERIFICATION = "NEW ACCOUNT ACTIVATION";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;
    @Value("${spring.mail.verify.host}")
    private String verifyHost;

    @Override
    public void sendRegistrationEmail(String email, Integer accountID, Integer confirmationID, String token, LocalDateTime expirationTime) {
        try {
            sendEmail(email, EmailUtils.getRegistrationMessage(accountID, confirmationID, token, expirationTime, verifyHost), NEW_ACCOUNT_VERIFICATION);
        } catch(Exception e){
            EmailUtils.handleEmailException("Error sending registration email to " + email + "at " + LocalDateTime.now());
        }
    }

    private void sendEmail(String toEmail, String content, String subject) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending email");
        }
    }
}
