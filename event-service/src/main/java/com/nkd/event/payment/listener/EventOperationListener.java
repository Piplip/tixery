package com.nkd.event.payment.listener;

import com.nkd.event.enumeration.EventOperationType;
import com.nkd.event.payment.EventOperation;
import com.nkd.event.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.nkd.event.Tables.*;

@Component
@RequiredArgsConstructor
public class EventOperationListener {

    private final DSLContext context;
    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void onEventOperation(EventOperation operation) {
        switch (operation.getType()){
            case EventOperationType.VIEW -> {
                var data = operation.getData();
                context.insertInto(EVENTVIEWS).set(EVENTVIEWS.EVENT_ID, UUID.fromString(String.valueOf(data.get("eventID"))))
                        .set(EVENTVIEWS.VIEW_DATE, OffsetDateTime.now())
                        .set(EVENTVIEWS.PROFILE_ID, (Integer) data.get("profileID"))
                        .execute();
            }
            case CANCEL -> {
                var data = operation.getData();
                String eventName = context.select(EVENTS.NAME).from(EVENTS)
                                .where(EVENTS.EVENT_ID.eq(UUID.fromString(String.valueOf(data.get("eventID")))))
                                .fetchOneInto(String.class);
                String username = data.get("username").toString();
                emailService.sendCancellationEmail((Integer) data.get("orderID"), eventName, (String) data.get("email"), username);
            }
        }
    }
}
