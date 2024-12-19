package com.nkd.accountservice.event.listener;

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
            case REGISTRATION -> emailService.sendRegistrationEmail(
                    (String) userEvent.getData().get("accountName"),
                    (String) userEvent.getData().get("email"),
                    (Integer) userEvent.getData().get("accountID"),
                    (Integer) userEvent.getData().get("confirmationID"),
                    (String) userEvent.getData().get("token"),
                    (LocalDateTime) userEvent.getData().get("expirationTime")
            );
        }
    }
}
