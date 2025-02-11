package com.nkd.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkd.event.client.AccountClient;
import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.OnlineEventDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.enumeration.EventOperationType;
import com.nkd.event.payment.EventOperation;
import com.nkd.event.utils.EventUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.stream.Collectors;

import static com.nkd.event.Tables.*;
import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final DSLContext context;
    private final AccountClient accountClient;
    private final ApplicationEventPublisher publisher;

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

                final JSONB locationJsonb = eventDTO.getLocationType().equals("venue") ? JSONB.jsonb("""
                    {
                        "location": "%s",
                        "locationType": "%s",
                        "reserveSeating": %s,
                        "lat": %s,
                        "lon": %s,
                        "name": "%s"
                    }
                """.formatted(
                        Optional.ofNullable(eventDTO.getLocation()).orElse("").replace("\r", "\\r").replace("\n", "\\n"),
                        Optional.of(eventDTO.getLocationType()).orElse("").replace("\r", "\\r").replace("\n", "\\n"),
                        Optional.ofNullable(eventDTO.getReserveSeating()).orElse(false),
                        Optional.ofNullable(eventDTO.getLatitude()).orElse("0.0"),
                        Optional.ofNullable(eventDTO.getLongitude()).orElse("0.0"),
                        Optional.ofNullable(eventDTO.getLocationName()).orElse("").replace("\r", "\\r").replace("\n", "\\n")
                )) : null;

                final List<String> faqJsonList = Optional.ofNullable(eventDTO.getFaqs())
                        .orElse(List.of())
                        .stream()
                        .map(faq -> """
                            {
                              "question": "%s",
                              "answer": "%s"
                            }
                        """.formatted(faq.getQuestion().replace("\r", "\\r").replace("\n", "\\n"), faq.getAnswer().replace("\r", "\\r").replace("\n", "\\n")))
                        .toList();

                final JSONB faqJsonArray = JSONB.jsonb("[" + String.join(",", faqJsonList) + "]");

                context.update(EVENTS)
                        .set(EVENTS.IMAGES, eventDTO.getImages())
                        .set(EVENTS.VIDEOS, eventDTO.getVideos())
                        .set(EVENTS.NAME, eventDTO.getTitle())
                        .set(EVENTS.SHORT_DESCRIPTION, eventDTO.getSummary())
                        .set(EVENTS.FULL_DESCRIPTION, eventDTO.getAdditionalInfo())
                        .set(EVENTS.IS_RECURRING, eventDTO.getEventType().equalsIgnoreCase("recurring"))
                        .set(EVENTS.START_TIME, eventStartTime)
                        .set(EVENTS.END_TIME, eventEndTime)
                        .set(EVENTS.SHOW_END_TIME, eventDTO.getDisplayEndTime())
                        .set(EVENTS.LANGUAGE, eventDTO.getLanguage())
                        .set(EVENTS.LOCATION, locationJsonb)
                        .set(EVENTS.FAQ, faqJsonArray)
                        .set(EVENTS.UPDATED_AT, OffsetDateTime.now().withOffsetSameLocal(offset))
                        .set(EVENTS.TIMEZONE, eventDTO.getTimezone())
                        .set(EVENTS.COORDINATES, Optional.ofNullable(eventDTO.getLatitude()).isPresent() && Optional.ofNullable(eventDTO.getLongitude()).isPresent()
                                ? field("ST_GeographyFromText('POINT(" + eventDTO.getLongitude() + " " + eventDTO.getLatitude() + ")')", String.class)
                                : null)
                        .where(EVENTS.EVENT_ID.eq(UUID.fromString(eid)))
                        .execute();
            }
            case "3" -> {
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

    // TODO: Gross, sold tickets will be implement later
    public List<Map<String, Object>> getAllEvents(Integer userID, String getPast) {
        Condition condition = EVENTS.ORGANIZER_ID.eq(userID);
        if (getPast.equalsIgnoreCase("false")) {
            condition = condition.and(EVENTS.START_TIME.gt(OffsetDateTime.now()));
        }
        else {
            condition = condition.and(EVENTS.START_TIME.lt(OffsetDateTime.now()));
        }

        var eventData = context.select(EVENTS.EVENT_ID, EVENTS.PROFILE_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.LOCATION, EVENTS.START_TIME, EVENTS.STATUS)
                .from(EVENTS)
                .where(condition)
                .orderBy(EVENTS.START_TIME.asc())
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

    public Map<String, Object> getEvent(String eventID, Integer profileID) {
        EventOperation viewOperation = EventOperation.builder()
                .data(Map.of("eventID", eventID, "profileID", profileID))
                .type(EventOperationType.VIEW)
                .build();
        publisher.publishEvent(viewOperation);

        var eventData = context.select(
                        EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.SHOW_END_TIME, EVENTS.SHORT_DESCRIPTION,
                        EVENTS.IMAGES, EVENTS.VIDEOS, EVENTS.START_TIME, EVENTS.END_TIME, EVENTS.LOCATION, EVENTS.CREATED_AT,
                        EVENTS.CATEGORY, EVENTS.SUB_CATEGORY, EVENTS.TAGS, EVENTS.STATUS, EVENTS.REFUND_POLICY, EVENTS.FAQ, EVENTS.FULL_DESCRIPTION,
                        EVENTS.CAPACITY, EVENTS.UPDATED_AT, EVENTS.LANGUAGE, EVENTS.IS_RECURRING, EVENTS.TIMEZONE, EVENTS.PROFILE_ID
                )
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneMap();

        if (eventData == null) {
            return null;
        }

        var tickets = context.selectFrom(TICKETTYPES)
                .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID))
                        .and(TICKETTYPES.STATUS.eq("visible")
                                .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                                        .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
                                .or(TICKETTYPES.STATUS.eq("custom")
                                        .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                                        .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now())))))
                .orderBy(TICKETTYPES.SALE_START_TIME)
                .fetchMaps();

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

    public List<Map<String, Object>> getProfileRelatedEvent(String eventID) {
        Integer organizerID = context.select(EVENTS.ORGANIZER_ID)
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .orderBy(EVENTS.START_TIME.asc())
                .fetchOneInto(Integer.class);

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.eq(organizerID).and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                        .and(EVENTS.EVENT_ID.ne(UUID.fromString(eventID.trim()))))
                .limit(6)
                .fetchMaps();

        return getEventTickets(eventRecord);
    }

    public List<Map<String, Object>> getAllProfileEvent(Integer profileID) {
        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(EVENTS)
                .where(EVENTS.PROFILE_ID.eq(profileID))
                .fetchMaps();

        return getEventTickets(eventRecord);
    }

    public List<Map<String, Object>> getEventTickets(List<Map<String, Object>> eventRecord) {
        eventRecord.forEach(event -> {
            var tickets = context.select(TICKETTYPES.PRICE, TICKETTYPES.TICKET_TYPE, TICKETTYPES.CURRENCY)
                    .from(TICKETTYPES)
                    .where(TICKETTYPES.EVENT_ID.eq((UUID) event.get("event_id"))
                            .and(TICKETTYPES.STATUS.eq("visible")
                                    .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                                            .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
                                    .or(TICKETTYPES.STATUS.eq("custom")
                                            .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                                            .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now())))))
                    .fetch();
            if(tickets.isEmpty()){
                event.put("price", null);
                return;
            }
            String leastPrice = tickets.stream()
                    .map(ticket -> new BigDecimal(ticket.get(TICKETTYPES.PRICE).toString()))
                    .collect(Collectors.collectingAndThen(Collectors.toList(), prices -> {
                        boolean allFree = prices.stream().allMatch(price -> price.compareTo(BigDecimal.ZERO) == 0);
                        boolean hasFree = prices.stream().anyMatch(price -> price.compareTo(BigDecimal.ZERO) == 0);
                        if (allFree) {
                            return "Free";
                        } else if (hasFree) {
                            return "0.0";
                        } else {
                            return prices.stream().min(BigDecimal::compareTo).map(BigDecimal::toString).orElse("Free");
                        }
                    }));
            event.put("price", leastPrice);
            event.put("currency", tickets.getFirst().get("currency"));
        });

        return eventRecord;
    }

    public List<Map<String, Object>> getSuggestedEvents(Integer limit) {
        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .distinctOn(EVENTS.ORGANIZER_ID)
                .from(EVENTS)
                .where(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                .limit(limit)
                .fetchMaps();

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getLocalizePopularEvents(String lat, String lon) {
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(POPULAREVENTS).join(EVENTS).on(POPULAREVENTS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(EVENTS.VISIBILITY.eq("public")
//                .and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 5000)
                .and(EVENTS.STATUS.eq("published"))
                .and(EVENTS.START_TIME.gt(OffsetDateTime.now())))
                .orderBy(POPULAREVENTS.VIEW_COUNT)
                .limit(10)
                .fetchMaps();

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public Response saveOnlineEventInfo(String eventID, OnlineEventDTO data) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Map<String, Object> map : data.getData()) {
            try {
                sb.append(new ObjectMapper().writeValueAsString(map)).append(",");
            } catch (JsonProcessingException e) {
                log.error("Error while converting map to string: {}", e.getMessage());
            }
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");

        var locationJsonData = JSONB.jsonb("""
                {
                    "locationType": "online",
                    "enabled": %s,
                    "access": "%s",
                    "data": %s
                }
            """.formatted(data.getEnabled(), data.getAccess(), sb.toString()));

        context.update(EVENTS)
                .set(EVENTS.LOCATION, locationJsonData)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    public Response addToFavourite(String eventID, Integer profileID) {
        context.insertInto(LIKEDEVENTS)
                .set(LIKEDEVENTS.PROFILE_ID, profileID)
                .set(LIKEDEVENTS.EVENT_ID, UUID.fromString(eventID))
                .execute();

        return new Response(HttpStatus.OK.name(), "Saved to favorite", null);
    }

    public List<UUID> getFavouriteEventIDs(Integer profileID) {
        return context.select(LIKEDEVENTS.EVENT_ID)
                .from(LIKEDEVENTS)
                .where(LIKEDEVENTS.PROFILE_ID.eq(profileID))
                .limit(10)
                .fetchInto(UUID.class);
    }

    public List<Map<String, Object>> getFavouriteEvents(List<String> eventIDs) {
        if(eventIDs.isEmpty()){
            return List.of();
        }

        List<UUID> eventIDList = eventIDs.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.LOCATION)
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.in(eventIDList))
                .fetchMaps();

        return getEventTickets(eventRecord);
    }

    public Response removeFromFavorite(String eventID, Integer profileID) {
        context.deleteFrom(LIKEDEVENTS)
                .where(LIKEDEVENTS.PROFILE_ID.eq(profileID).and(LIKEDEVENTS.EVENT_ID.eq(UUID.fromString(eventID))))
                .execute();

        return new Response(HttpStatus.OK.name(), "Removed successful", null);
    }

    public Integer getTotalFavouriteEvent(Integer profileID) {
        return context.selectCount()
                .from(LIKEDEVENTS)
                .where(LIKEDEVENTS.PROFILE_ID.eq(profileID))
                .fetchOneInto(Integer.class);
    }

    public List<Map<String, Object>> getFollowedEvents(List<Integer> organizerIDs) {
        if (organizerIDs.isEmpty()) {
            return List.of();
        }

        var subquery = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.LOCATION,
                        rowNumber().over(partitionBy(EVENTS.ORGANIZER_ID).orderBy(EVENTS.START_TIME.asc())).as("row_num")
                )
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.in(organizerIDs).and(EVENTS.START_TIME.gt(OffsetDateTime.now())))
                .asTable("foo");

        var eventRecord = context.select(subquery.field(EVENTS.EVENT_ID), subquery.field(EVENTS.NAME), subquery.field(EVENTS.EVENT_TYPE),
                        subquery.field(EVENTS.IMAGES), subquery.field(EVENTS.START_TIME), subquery.field(EVENTS.LOCATION)
                )
                .from(subquery)
                .where(field("row_num").le(2))
                .fetchMaps();

        return getEventTickets(eventRecord);
    }

    public List<Map<String, Object>> getProfileOrders(Integer profileID, Integer lastOrderID, Boolean getPast) {
        Condition condition = trueCondition();
        if (lastOrderID != null) {
            condition = ORDERS.ORDER_ID.gt(lastOrderID);
        }
        if (getPast) {
            condition = condition.and(EVENTS.START_TIME.lt(OffsetDateTime.now()));
        }
        else {
            condition = condition.and(EVENTS.START_TIME.gt(OffsetDateTime.now()));
        }

        return context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.START_TIME, EVENTS.IMAGES, EVENTS.LOCATION,
                        ORDERS.ORDER_ID, ORDERS.CREATED_AT)
                .from(ORDERS)
                .join(EVENTS).on(ORDERS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(ORDERS.PROFILE_ID.eq(profileID).and(ORDERS.STATUS.eq("paid"))
                        .and(condition))
                .orderBy(ORDERS.CREATED_AT.desc())
                .limit(10)
                .fetchMaps();
    }

    public List<Map<String, Object>> getEventRecord(Condition condition, String lat, String lon, Table<?> view){
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        condition = condition.and(EVENTS.STATUS.eq("published"))
                .and(EVENTS.START_TIME.gt(OffsetDateTime.now()));
//                .and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 5000);

        return context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.EVENT_TYPE, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .distinctOn(EVENTS.PROFILE_ID)
                .from(view)
                .where(condition)
                .orderBy(EVENTS.PROFILE_ID)
                .limit(8)
                .fetchMaps();
    }

    public List<Map<String, Object>> getOnlineEvents(String lat, String lon) {
        var eventRecord = getEventRecord(
                DSL.trueCondition().and(String.valueOf(EVENTS.LOCATION.toString().contains("online"))), lat, lon, EVENTS);

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getSuggestedEventByTime(String lat, String lon, String timeType) {
        Condition condition = EventUtils.constructTimeCondition(timeType);

        var eventRecord = getEventRecord(condition, lat, lon, EVENTS);

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getSuggestedEventByType(String lat, String lon, String eventType) {
        var eventRecord = getEventRecord(EVENTS.EVENT_TYPE.equalIgnoreCase(eventType), lat, lon, EVENTS);
        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    // TODO: Fix this: wrong condition for getting events by cost (especially free events)
    public List<Map<String, Object>> getSuggestedEventsByCost(String lat, String lon, Double cost) {
        var eventRecord = getEventRecord(TICKETTYPES.PRICE.lessOrEqual(BigDecimal.valueOf(cost)), lat, lon,
                EVENTS.join(TICKETTYPES).on(EVENTS.EVENT_ID.eq(TICKETTYPES.EVENT_ID)));

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getListOrganizerEvent(List<Map<String, Object>> eventRecord) {
        String profileIdList = eventRecord.stream()
                .map(event -> event.get("profile_id").toString())
                .collect(Collectors.joining(","));

        if(profileIdList.isEmpty()){
            return List.of();
        }

        var listProfileName = accountClient.getListProfileName(profileIdList);
        eventRecord.forEach(event -> {
            Optional<Integer> profileId = Optional.ofNullable(((Integer) event.get("profile_id")));
            profileId.ifPresent(profileID -> event.put("profileName", listProfileName.get(profileID)));
        });

        return eventRecord;
    }
}
