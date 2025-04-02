package com.nkd.event.service;

import com.nkd.event.dto.PaymentDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;
    @Value("${client.host}")
    private String clientHost;

    public void sendPaymentSuccessEmail(PaymentDTO paymentDTO, Map<String, Object> organizerData) {
        Context context = new Context();
        context.setVariable("username", paymentDTO.getUsername());
        context.setVariable("clientHost", clientHost);
        context.setVariable("profileID", paymentDTO.getProfileID());
        context.setVariable("organizerName", organizerData.get("profile_name"));
        String content = templateEngine.process("payment_success", context);
        String organizerContent = templateEngine.process("organizer_payment_success", context);

        try {
            sendEmail(paymentDTO.getEmail(), content, "Payment Success");
            sendEmail(organizerData.get("account_email").toString(), organizerContent, "WOOHOO! A NEW ORDER FOR YOUR EVENT HAS BEEN PLACED!");
        } catch (Exception e) {
            handleEmailException("Error sending payment success email to " + paymentDTO.getEmail());
        }
    }

    public void sendEmail(String toEmail, String content, String subject) {
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

    private void handleEmailException(String message) {
        log.error(message);
        throw new RuntimeException(message);
    }

    public void sendCancellationEmail(Integer orderID, String eventName, String email, String username) {
        Context context = new Context();
        context.setVariable("orderID", orderID);
        context.setVariable("eventName", eventName);
        context.setVariable("username", username);
        String content = templateEngine.process("cancel_order", context);

        try {
            sendEmail(email, content, "Order Cancellation");
        } catch (Exception e) {
            handleEmailException("Error sending payment success email to " + email
            );
        }
    }
}
