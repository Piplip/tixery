package com.nkd.event.service;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static com.nkd.event.Tables.EVENTS;

@Component
@RequiredArgsConstructor
public class ScheduledTask {

    private final DSLContext context;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void updateEventStatus(){
        context.update(EVENTS)
                .set(EVENTS.STATUS, "past")
                .where(EVENTS.END_TIME.lessThan(OffsetDateTime.now()))
                .execute();
    }
}
