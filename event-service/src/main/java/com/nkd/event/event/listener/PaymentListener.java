package com.nkd.event.event.listener;

import com.nkd.event.client.AccountClient;
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
    private final AccountClient accountClient;

    @Async("taskExecutor")
    @EventListener
    public void handlePaymentSuccess(PaymentDTO payment) {
        var organizerData = accountClient.getAccountData(payment.getOrganizerID());
        emailService.sendPaymentSuccessEmail(payment, organizerData);
    }
}
