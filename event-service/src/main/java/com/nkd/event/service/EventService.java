package com.nkd.event.service;

import com.nkd.event.client.AccountClient;
import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.utils.EventUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.nkd.event.Tables.EVENTS;
import static com.nkd.event.Tables.TICKETTYPES;
import static org.jooq.impl.DSL.sum;

@Service
@RequiredArgsConstructor
public class EventService {

    private final DSLContext context;
    private final AccountClient accountClient;

    public Response createEvent(EventDTO eventDTO, String eid, String step) {
        if (eid == null || eid.isEmpty()) {
            return new Response(HttpStatus.BAD_REQUEST.name(), "Event ID is absent", null);
        }
        try {
            UUID.fromString(eid);
        } catch (IllegalArgumentException e) {
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid Event ID format", null);
        }

        final ZoneOffset offset = ZoneOffset.ofHours(Integer.parseInt(eventDTO.getTimezone()));
        switch (step) {
            case "0" -> {
                final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                final LocalDate eventDate = LocalDate.parse(eventDTO.getEventDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                final LocalTime startTime = LocalTime.parse(eventDTO.getEventStartTime(), timeFormatter);
                final LocalTime endTime = LocalTime.parse(eventDTO.getEventEndTime(), timeFormatter);

                final OffsetDateTime eventStartTime = eventDate.atTime(startTime).atOffset(offset);
                final OffsetDateTime eventEndTime = eventDate.atTime(endTime).atOffset(offset);

                final JSONB locationJsonb = JSONB.jsonb("""
                    {
                        "location": "%s",
                        "locationType": "%s",
                        "reserveSeating": %s
                    }
                    """.formatted(
                        eventDTO.getLocation(),
                        eventDTO.getLocationType(),
                        eventDTO.getReserveSeating()
                ));

                final List<String> faqJsonList = eventDTO.getFaqs().stream()
                        .map(faq -> """
                            {
                              "question": "%s",
                              "answer": "%s"
                            }
                        """.formatted(faq.getQuestion(), faq.getAnswer()))
                        .toList();

                final String faqJsonArray = "[" + String.join(",", faqJsonList) + "]";
                final Field<JSONB> faqJsonbField = DSL.field("cast({0} as jsonb)", JSONB.class, faqJsonArray);

                context.update(EVENTS)
                        .set(EVENTS.IMAGES, eventDTO.getImages())
                        .set(EVENTS.VIDEOS, eventDTO.getVideos())
                        .set(EVENTS.NAME, eventDTO.getTitle())
                        .set(EVENTS.DESCRIPTION, eventDTO.getSummary())
                        .set(EVENTS.IS_RECURRING, eventDTO.getEventType().equalsIgnoreCase("recurring"))
                        .set(EVENTS.START_TIME, eventStartTime)
                        .set(EVENTS.END_TIME, eventEndTime)
                        .set(EVENTS.SHOW_END_TIME, eventDTO.getDisplayEndTime())
                        .set(EVENTS.LANGUAGE, eventDTO.getLanguage())
                        .set(EVENTS.LOCATION, locationJsonb)
                        .set(EVENTS.FAQ, faqJsonbField)
                        .set(EVENTS.UPDATED_AT, OffsetDateTime.now().withOffsetSameLocal(offset))
                        .set(EVENTS.TIMEZONE, eventDTO.getTimezone())
                        .where(EVENTS.EVENT_ID.eq(UUID.fromString(eid)))
                        .execute();
            }
            case "2" -> {
                JSONB refundJSON = null;
                if(eventDTO.getAllowRefund()){
                    refundJSON = JSONB.jsonb("""
                            {
                                "allowRefund": %b,
                                "daysForRefund": %d,
                                "automateRefund": %b
                            }
                            """.formatted(true, eventDTO.getDaysForRefund(), eventDTO.getAutomatedRefund())
                    );
                }

                OffsetDateTime publishTime = OffsetDateTime.now();
                if(eventDTO.getPublishType().equalsIgnoreCase("scheduled")){
                    publishTime = EventUtils.convertToOffsetDateTime(eventDTO.getPublishDate(), eventDTO.getPublishTime()
                            , Integer.parseInt(eventDTO.getTimezone()));
                }

                context.update(EVENTS)
                        .set(EVENTS.EVENT_TYPE, eventDTO.getType())
                        .set(EVENTS.CATEGORY, eventDTO.getCategory())
                        .set(EVENTS.SUB_CATEGORY, eventDTO.getSubCategory())
                        .set(EVENTS.TAGS, eventDTO.getTags())
                        .set(EVENTS.VISIBILITY, eventDTO.getEventVisibility())
                        .set(EVENTS.REFUND_POLICY, refundJSON)
                        .set(EVENTS.STATUS, eventDTO.getPublishType().equals("now") ? "published" : "scheduled")
                        .set(EVENTS.PUBLISH_DATETIME, publishTime)
                        .set(EVENTS.CAPACITY, eventDTO.getCapacity())
                        .set(EVENTS.UPDATED_AT, OffsetDateTime.now().withOffsetSameLocal(offset))
                        .where(EVENTS.EVENT_ID.eq(UUID.fromString(eid)))
                        .execute();
            }
        }
        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    public Response createEventRequest(String pid, String u) {
        Integer userID = accountClient.getUserID(u);

        var eventID = context.insertInto(EVENTS)
                .set(EVENTS.PROFILE_ID, Integer.parseInt(pid))
                .set(EVENTS.ORGANIZER_ID, userID)
                .set(EVENTS.STATUS, "draft")
                .returningResult(EVENTS.EVENT_ID)
                .fetchOneInto(UUID.class);

        return new Response(HttpStatus.OK.name(), "OK", eventID);
    }

    public Response addTicket(String eventID, TicketDTO ticket, Integer timezone) {
        var salesTime = EventUtils.transformDate(ticket.getStartDate(), ticket.getEndDate(),
                ticket.getStartTime(), ticket.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;
        
        if(ticket.getVisibility().equalsIgnoreCase("custom")){
            visDuration = EventUtils.transformDate(ticket.getVisibleStartDate(), ticket.getVisibleEndDate(),
                    ticket.getVisibleStartTime(), ticket.getVisibleEndTime(), timezone);
        }

        var ticketID = context.insertInto(TICKETTYPES)
                .set(TICKETTYPES.EVENT_ID, UUID.fromString(eventID))
                .set(TICKETTYPES.TICKET_TYPE, ticket.getTicketType())
                .set(TICKETTYPES.NAME, ticket.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticket.getQuantity())
                .set(TICKETTYPES.PRICE, ticket.getTicketType().equalsIgnoreCase("paid")
                        ? BigDecimal.valueOf(Double.parseDouble(ticket.getPrice())) : BigDecimal.ZERO)
                .set(TICKETTYPES.DESCRIPTION, ticket.getDescription())
                .set(TICKETTYPES.STATUS, ticket.getVisibility())
                .set(TICKETTYPES.SALE_START_TIME, salesTime.getFirst())
                .set(TICKETTYPES.SALE_END_TIME, salesTime.getSecond())
                .set(TICKETTYPES.VIS_START_TIME, visDuration != null ? visDuration.getFirst() : null)
                .set(TICKETTYPES.VIS_END_TIME, visDuration != null ? visDuration.getSecond() : null)
                .set(TICKETTYPES.ABSORB_FEE, ticket.getTicketType().equalsIgnoreCase("donation") ? ticket.getAbsorbFee() : null)
                .set(TICKETTYPES.MIN_PER_ORDER, ticket.getMinPerOrder())
                .set(TICKETTYPES.MAX_PER_ORDER, ticket.getMaxPerOrder())
                .returningResult(TICKETTYPES.TICKET_TYPE_ID)
                .fetchOneInto(Integer.class);
                
        return new Response(HttpStatus.OK.name(), "OK", ticketID);
    }

    public Response updateTicket(Integer ticketID, TicketDTO ticketDTO, Integer timezone) {
        var salesTime = EventUtils.transformDate(ticketDTO.getStartDate(), ticketDTO.getEndDate(),
                ticketDTO.getStartTime(), ticketDTO.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;

        if (ticketDTO.getVisibility().equalsIgnoreCase("custom")) {
            visDuration = EventUtils.transformDate(ticketDTO.getVisibleStartDate(), ticketDTO.getVisibleEndDate(),
                    ticketDTO.getVisibleStartTime(), ticketDTO.getVisibleEndTime(), timezone);
        }

        int rowsUpdated = context.update(TICKETTYPES)
                .set(TICKETTYPES.TICKET_TYPE, ticketDTO.getTicketType())
                .set(TICKETTYPES.NAME, ticketDTO.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticketDTO.getQuantity())
                .set(TICKETTYPES.PRICE, ticketDTO.getTicketType().equalsIgnoreCase("paid")
                        ? BigDecimal.valueOf(Double.parseDouble(ticketDTO.getPrice())) : BigDecimal.ZERO)
                .set(TICKETTYPES.DESCRIPTION, ticketDTO.getDescription())
                .set(TICKETTYPES.STATUS, ticketDTO.getVisibility())
                .set(TICKETTYPES.SALE_START_TIME, salesTime.getFirst())
                .set(TICKETTYPES.SALE_END_TIME, salesTime.getSecond())
                .set(TICKETTYPES.VIS_START_TIME, visDuration != null ? visDuration.getFirst() : null)
                .set(TICKETTYPES.VIS_END_TIME, visDuration != null ? visDuration.getSecond() : null)
                .set(TICKETTYPES.ABSORB_FEE, ticketDTO.getTicketType().equalsIgnoreCase("donation") ? ticketDTO.getAbsorbFee() : null)
                .set(TICKETTYPES.MIN_PER_ORDER, ticketDTO.getMinPerOrder())
                .set(TICKETTYPES.MAX_PER_ORDER, ticketDTO.getMaxPerOrder())
                .set(TICKETTYPES.UPDATED_AT, OffsetDateTime.now())
                .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticketID))
                .execute();

        if (rowsUpdated > 0) {
            return new Response(HttpStatus.OK.name(), "Ticket updated successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Ticket not found", null);
        }
    }

    @Transactional
    public Response deleteTicket(Integer ticketID) {
        int rowsDeleted = context.deleteFrom(TICKETTYPES)
                .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticketID))
                .execute();

        if (rowsDeleted > 0) {
            return new Response(HttpStatus.OK.name(), "Ticket deleted successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Ticket not found", null);
        }
    }

    // TODO: Gross, sold tickets will be implement later
    public List<Map<String, Object>> getAllEvents(Integer userID) {
        var eventData = context.select(EVENTS.EVENT_ID, EVENTS.PROFILE_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.LOCATION, EVENTS.START_TIME, EVENTS.STATUS)
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.eq(userID))
                .orderBy(EVENTS.START_TIME.desc())
                .fetchMaps();

        eventData.forEach(event -> {
            Optional<Integer> ticketCount = context.select(sum(TICKETTYPES.QUANTITY))
                    .from(TICKETTYPES)
                    .where(TICKETTYPES.EVENT_ID.eq((UUID) event.get("event_id")))
                    .fetchOptionalInto(Integer.class);
            Object images = event.get("images");
            if(images != null){
                event.put("images", images);
            }
            event.put("ticketCount", ticketCount.orElse(0));
        });

        return eventData;
    }

    public Map<String, Object> getEvent(String eventID) {
        var eventRecord = context.select(
                        EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.SHOW_END_TIME, EVENTS.DESCRIPTION,
                        EVENTS.IMAGES, EVENTS.VIDEOS, EVENTS.START_TIME, EVENTS.END_TIME, EVENTS.LOCATION,
                        EVENTS.CATEGORY, EVENTS.SUB_CATEGORY, EVENTS.TAGS, EVENTS.STATUS, EVENTS.REFUND_POLICY, EVENTS.FAQ,
                        EVENTS.CAPACITY, EVENTS.UPDATED_AT, EVENTS.LANGUAGE, EVENTS.IS_RECURRING, EVENTS.TIMEZONE, EVENTS.PROFILE_ID
                )
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOne();

        if (eventRecord == null) {
            return null;
        }
        var tickets = context.selectFrom(TICKETTYPES)
                .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchMaps();
        
        Map<String, Object> eventData = eventRecord.intoMap();
        eventData.put("tickets", tickets);
        
        return eventData;
        
    }

    @Transactional
    public Response deleteEvent(String eventID) {
        int rowsDeleted = context.deleteFrom(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();

        if (rowsDeleted > 0) {
            return new Response(HttpStatus.OK.name(), "Event deleted successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Event not found", null);
        }
    }

    public List<Map<String, Object>> getRelatedEvents(String eventID) {
        Integer organizerID = context.select(EVENTS.ORGANIZER_ID)
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneInto(Integer.class);

        var eventRecord = context.select(
                        EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.LOCATION,
                        EVENTS.REFUND_POLICY, EVENTS.FAQ
                )
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.eq(organizerID).and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                        .and(EVENTS.EVENT_ID.ne(UUID.fromString(eventID.trim()))))
                .fetchMaps();
        
        eventRecord.forEach(event -> {
            var tickets = context.select(TICKETTYPES.PRICE, TICKETTYPES.TICKET_TYPE)
                    .from(TICKETTYPES)
                    .where(TICKETTYPES.EVENT_ID.eq((UUID) event.get("event_id")))
                    .fetch();
            String leastPrice = tickets.stream()
                    .filter(ticket -> ticket.get(TICKETTYPES.TICKET_TYPE).equals("paid"))
                    .map(ticket -> ticket.get(TICKETTYPES.PRICE).toString())
                    .min(String::compareTo)
                    .orElse("Free");
            event.put("price", leastPrice);
        });

        return eventRecord;
    }
}
