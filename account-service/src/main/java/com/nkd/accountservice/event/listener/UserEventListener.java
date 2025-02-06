package com.nkd.accountservice.event.listener;

import com.nkd.accountservice.enumeration.EventType;
import com.nkd.accountservice.event.UserEvent;
import com.nkd.accountservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final EmailService emailService;

    @EventListener
    public void handleAccountEvent(UserEvent userEvent){
        switch (userEvent.getEventType()){
            case EventType.REGISTRATION -> emailService.sendRegistrationEmail(
                    (String) userEvent.getData().get("email"),
                    (Integer) userEvent.getData().get("accountID"),
                    (Integer) userEvent.getData().get("confirmationID"),
                    (String) userEvent.getData().get("token"),
                    (LocalDateTime) userEvent.getData().get("expirationTime")
            );
            case EventType.RESEND_ACTIVATION -> emailService.sendActivationEmail(
                    (String) userEvent.getData().get("email"),
                    (Integer) userEvent.getData().get("accountID"),
                    (Integer) userEvent.getData().get("confirmationID"),
                    (String) userEvent.getData().get("token"),
                    (LocalDateTime) userEvent.getData().get("expirationTime")
            );
            case EventType.FORGOT_PASSWORD -> emailService.sendPasswordResetEmail(
                    (String) userEvent.getData().get("email"),
                    (String) userEvent.getData().get("code"),
                    (LocalDateTime) userEvent.getData().get("expirationTime")
            );
            case OAUTH2_SET_PASSWORD -> emailService.sendOAuth2SetPasswordEmail((String) userEvent.getData().get("email"));
        }
    }
}
