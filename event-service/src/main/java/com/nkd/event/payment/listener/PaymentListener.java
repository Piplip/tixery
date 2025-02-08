package com.nkd.event.payment.listener;

import com.nkd.event.dto.PaymentDTO;
import com.nkd.event.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final EmailService emailService;

    @Async
    @EventListener
    public void handlePaymentSuccess(PaymentDTO payment) {
        emailService.sendPaymentSuccessEmail(payment);
    }
}
