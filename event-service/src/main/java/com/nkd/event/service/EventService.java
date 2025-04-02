package com.nkd.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkd.event.client.AccountClient;
import com.nkd.event.client.SuggestionClient;
import com.nkd.event.dto.*;
import com.nkd.event.enumeration.EventOperationType;
import com.nkd.event.event.EventOperation;
import com.nkd.event.utils.EventUtils;
import com.nkd.event.utils.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.nkd.event.Tables.*;
import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final DSLContext context;
    private final AccountClient accountClient;
    private final SuggestionClient suggestionClient;
    private final TicketService ticketService;
    private final EmailService emailService;
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

                final JSONB locationJsonb = EventUtils.constructLocation(eventDTO.getLocationType(), eventDTO.getLatitude(), eventDTO.getLongitude()
                        , eventDTO.getLocation(), eventDTO.getLocationName());

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
                        .set(EVENTS.RESERVE_SEATING, eventDTO.getReserveSeating())
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
            case "3" -> context.update(EVENTS)
                    .set(EVENTS.CAPACITY, eventDTO.getCapacity())
                    .where(EVENTS.EVENT_ID.eq(UUID.fromString(eid)))
                    .execute();
            case "4" -> {
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
                        .set(EVENTS.EVENT_TYPE_ID, context.select(EVENTTYPES.EVENT_TYPE_ID).from(EVENTTYPES)
                                .where(EVENTTYPES.NAME.equalIgnoreCase(eventDTO.getType())).fetchOneInto(Integer.class))
                        .set(EVENTS.SUB_CATEGORY_ID, context.select(SUBCATEGORIES.SUB_CATEGORY_ID).from(SUBCATEGORIES)
                                .where(SUBCATEGORIES.NAME.eq(eventDTO.getSubCategory())).fetchOneInto(Integer.class))
                        .set(EVENTS.TAGS, eventDTO.getTags())
                        .set(EVENTS.VISIBILITY, eventDTO.getEventVisibility())
                        .set(EVENTS.REFUND_POLICY, refundJSON)
                        .set(EVENTS.STATUS, eventDTO.getPublishType().equals("now") ? "published" : "scheduled")
                        .set(EVENTS.PUBLISH_DATETIME, publishTime)
                        .set(EVENTS.UPDATED_AT, OffsetDateTime.now().withOffsetSameLocal(offset))
                        .where(EVENTS.EVENT_ID.eq(UUID.fromString(eid)))
                        .execute();

                if(eventDTO.getReserveSeating() != null && eventDTO.getReserveSeating()){
                    createTierTicket(eid, eventDTO.getTierData());
                }
            }
        }
        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    private void createTierTicket(String eventID, List<Tier> tierData){
        if(tierData.isEmpty()){
            return;
        }

        tierData.forEach(tier -> {
            if(tier.getAssignedSeats() == null || tier.getAssignedSeats().isEmpty()){
                return;
            }
            Integer ticketTypeID = context.select(TICKETTYPES.TICKET_TYPE_ID).from(TICKETTYPES)
                            .where(TICKETTYPES.SEAT_TIER_ID.eq(Integer.parseInt(tier.getDbTierID())))
                                    .fetchOneInto(Integer.class);
            tier.getAssignedSeats().forEach(seat -> context.insertInto(TICKETS)
                    .set(TICKETS.EVENT_ID, UUID.fromString(eventID))
                    .set(TICKETS.TICKET_TYPE_ID, ticketTypeID)
                    .set(TICKETS.STATUS, "available")
                    .set(TICKETS.SEAT_IDENTIFIER, seat)
                    .execute());
        });
    }

    public Response saveSeatMap(String eventID, SeatMapDTO data) {
        String coords = context.select(EVENTS.COORDINATES).from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneInto(String.class);

        var seatMapID = context.insertInto(SEATMAP)
                .set(SEATMAP.OWNER_ID, data.getOwnerID())
                .set(SEATMAP.NAME, data.getName())
                .set(SEATMAP.MAP_URL, data.getMapURL())
                .set(SEATMAP.EVENT_ID, UUID.fromString(eventID))
                .set(SEATMAP.COORDINATES, coords != null ? field("?::geography", String.class, coords) : null)
                .set(SEATMAP.IS_PUBLIC, data.getIsPublic())
                .set(SEATMAP.CREATED_AT, OffsetDateTime.now())
                .returningResult(SEATMAP.MAP_ID)
                .fetchOneInto(Integer.class);

        List<Integer> tierIDs = data.getTiers().stream()
                .map(tier -> context.insertInto(SEATTIERS)
                        .set(SEATTIERS.MAP_ID, seatMapID)
                        .set(SEATTIERS.NAME, tier.getName())
                        .set(SEATTIERS.PERKS, tier.getPerks())
                        .set(SEATTIERS.TIER_COLOR, tier.getColor())
                        .set(SEATTIERS.CREATED_AT, OffsetDateTime.now())
                        .set(SEATTIERS.ASSIGNEDSEATS, tier.getTotalAssignedSeats())
                        .returningResult(SEATTIERS.SEAT_TIER_ID)
                        .fetchOneInto(Integer.class))
                .toList();

        context.update(EVENTS)
                .set(EVENTS.CAPACITY, data.getCapacity())
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "OK", tierIDs);
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

    public List<Map<String, Object>> getAllEvents(Integer userID, String getPast) {
        Condition condition = EVENTS.ORGANIZER_ID.eq(userID);
        if (getPast.equalsIgnoreCase("false")) {
            condition = condition.and(EVENTS.END_TIME.gt(OffsetDateTime.now()));
        } else {
            condition = condition.and(EVENTS.END_TIME.lt(OffsetDateTime.now()));
        }

        var eventData = context.select(EVENTS.EVENT_ID, EVENTS.PROFILE_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.LOCATION, EVENTS.IS_RECURRING,
                        EVENTS.START_TIME, EVENTS.END_TIME, EVENTS.STATUS)
                .from(EVENTS)
                .where(condition)
                .orderBy(EVENTS.START_TIME.asc())
                .fetchMaps();

        List<UUID> eventIds = eventData.stream()
                .map(event -> (UUID) event.get("event_id"))
                .toList();

        Map<UUID, Record3<UUID, String, BigDecimal>> grossData = context.select(ORDERS.EVENT_ID, PAYMENTS.CURRENCY, sum(PAYMENTS.AMOUNT).as("gross"))
                .from(ORDERS).join(PAYMENTS).on(ORDERS.PAYMENT_ID.eq(PAYMENTS.PAYMENT_ID))
                .where(ORDERS.STATUS.eq("paid").and(ORDERS.EVENT_ID.in(eventIds)))
                .groupBy(ORDERS.EVENT_ID, PAYMENTS.CURRENCY)
                .fetchMap(ORDERS.EVENT_ID);

        Condition ticketCondition = TICKETTYPES.STATUS.eq("visible")
                .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                        .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
                .or(TICKETTYPES.STATUS.eq("custom")
                        .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                        .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now())));

        Map<UUID, List<Map<String, Object>>> ticketsByEvent = new HashMap<>();

        context.select(
                        TICKETTYPES.EVENT_ID,
                        TICKETTYPES.TICKET_TYPE_ID,
                        TICKETTYPES.NAME,
                        SEATTIERS.NAME.as("tier_name"),
                        SEATTIERS.TIER_COLOR,
                        SEATTIERS.PERKS,
                        TICKETTYPES.PRICE,
                        TICKETTYPES.CURRENCY,
                        TICKETTYPES.TICKET_TYPE,
                        coalesce(TICKETTYPES.QUANTITY, 0).as("quantity"),
                        coalesce(TICKETTYPES.AVAILABLE_QUANTITY, 0).as("available_quantity"),
                        TICKETTYPES.SALE_START_TIME,
                        TICKETTYPES.SALE_END_TIME,
                        TICKETTYPES.STATUS
                )
                .from(TICKETTYPES).leftJoin(SEATTIERS).on(TICKETTYPES.SEAT_TIER_ID.eq(SEATTIERS.SEAT_TIER_ID))
                .where(TICKETTYPES.EVENT_ID.in(eventIds).and(ticketCondition))
                .fetch()
                .forEach(record -> {
                    UUID eventId = record.get(TICKETTYPES.EVENT_ID);
                    Map<String, Object> ticketInfo = new HashMap<>();
                    ticketInfo.put("ticket_type_id", record.get(TICKETTYPES.TICKET_TYPE_ID));
                    ticketInfo.put("name", record.get(TICKETTYPES.NAME));
                    ticketInfo.put("tier_name", record.get("tier_name"));
                    ticketInfo.put("tier_color", record.get("tier_color"));
                    ticketInfo.put("perks", record.get("perks"));
                    ticketInfo.put("price", record.get(TICKETTYPES.PRICE));
                    ticketInfo.put("currency", record.get(TICKETTYPES.CURRENCY));
                    ticketInfo.put("ticket_type", record.get(TICKETTYPES.TICKET_TYPE));
                    ticketInfo.put("quantity", record.get("quantity"));
                    ticketInfo.put("available_quantity", record.get("available_quantity"));
                    ticketInfo.put("sale_start_time", record.get(TICKETTYPES.SALE_START_TIME));
                    ticketInfo.put("sale_end_time", record.get(TICKETTYPES.SALE_END_TIME));
                    ticketInfo.put("status", record.get(TICKETTYPES.STATUS));

                    ticketsByEvent.computeIfAbsent(eventId, k -> new ArrayList<>()).add(ticketInfo);
                });

        eventData.forEach(event -> {
            UUID eventId = (UUID) event.get("event_id");
            List<Map<String, Object>> tickets = ticketsByEvent.getOrDefault(eventId, List.of());

            int ticketCount = tickets.stream()
                    .mapToInt(t -> ((Number)t.get("quantity")).intValue())
                    .sum();

            int remainingTicket = tickets.stream()
                    .mapToInt(t -> ((Number)t.get("available_quantity")).intValue())
                    .sum();

            Object images = event.get("images");
            if (images != null) {
                event.put("images", images);
            }

            Record3<UUID, String, BigDecimal> record = grossData.get(eventId);
            if (record != null) {
                event.put("gross", record.get("gross", BigDecimal.class));
                event.put("currency", record.get(PAYMENTS.CURRENCY));
            } else {
                event.put("gross", BigDecimal.ZERO);
                event.put("currency", "USD");
            }

            event.put("ticketCount", ticketCount);
            event.put("remainingTicket", remainingTicket);
            event.put("tickets", tickets);
        });

        return eventData;
    }

    public Map<String, Object> getEvent(String eventID, Integer profileID, Boolean isOrganizer, Integer timezone) {
        if(!Optional.ofNullable(isOrganizer).orElse(false) && profileID != null){
            EventOperation viewOperation = EventOperation.builder()
                    .data(Map.of("eventID", eventID, "profileID", profileID, "timezone", timezone))
                    .type(EventOperationType.VIEW)
                    .build();
            publisher.publishEvent(viewOperation);
        }

        var eventData = context.select(
                        EVENTS.EVENT_ID, EVENTS.NAME, EVENTTYPES.NAME.as("event_type"), EVENTS.SHOW_END_TIME, EVENTS.SHORT_DESCRIPTION,
                        EVENTS.IMAGES, EVENTS.VIDEOS, EVENTS.START_TIME, EVENTS.END_TIME, EVENTS.LOCATION, EVENTS.CREATED_AT, SEATMAP.MAP_ID, SEATMAP.MAP_URL,
                        CATEGORIES.NAME.as("category"), SUBCATEGORIES.NAME.as("sub_category"), EVENTS.TAGS, EVENTS.STATUS, EVENTS.REFUND_POLICY,
                        EVENTS.FAQ, EVENTS.FULL_DESCRIPTION, EVENTS.CAPACITY, EVENTS.UPDATED_AT, EVENTS.LANGUAGE, EVENTS.IS_RECURRING, EVENTS.TIMEZONE, EVENTS.PROFILE_ID
                )
                .from(EVENTS).leftJoin(SUBCATEGORIES).on(EVENTS.SUB_CATEGORY_ID.eq(SUBCATEGORIES.SUB_CATEGORY_ID))
                .leftJoin(SEATMAP).on(SEATMAP.EVENT_ID.eq(EVENTS.EVENT_ID))
                .leftJoin(CATEGORIES).on(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID))
                .leftJoin(EVENTTYPES).on(EVENTTYPES.EVENT_TYPE_ID.eq(EVENTS.EVENT_TYPE_ID))
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneMap();

        if (eventData == null) {
            return null;
        }

        if(eventData.get("map_id") != null){
            eventData.put("reserveSeating", true);
        }

        if(eventData.get("is_recurring").equals(true)){
            var occurrenceData = context.select(EVENTOCCURRENCES.OCCURRENCE_ID, EVENTOCCURRENCES.START_DATE, EVENTOCCURRENCES.START_TIME, EVENTOCCURRENCES.END_TIME)
                .from(EVENTOCCURRENCES)
                .where(EVENTOCCURRENCES.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchMaps();

            eventData.put("occurrences", occurrenceData);
            var ticketOccurrenceData = context.select(TICKETTYPEOCCURRENCES.TICKET_TYPE_OCCURRENCE_ID, TICKETTYPEOCCURRENCES.TICKET_TYPE_ID, TICKETTYPEOCCURRENCES.OCCURRENCE_ID)
                    .from(TICKETTYPEOCCURRENCES).join(TICKETTYPES).on(TICKETTYPES.TICKET_TYPE_ID.eq(TICKETTYPEOCCURRENCES.TICKET_TYPE_ID))
                    .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID)))
                    .fetchMaps();
            eventData.put("ticketOccurrences", ticketOccurrenceData);
        }

        var tickets = context.select(TICKETTYPES.asterisk(), SEATTIERS.SEAT_TIER_ID.as("tier_id"), SEATTIERS.TIER_COLOR, SEATTIERS.PERKS)
                .from(TICKETTYPES).leftJoin(SEATTIERS).on(TICKETTYPES.SEAT_TIER_ID.eq(SEATTIERS.SEAT_TIER_ID))
                .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID))
                        .and(Boolean.TRUE.equals(isOrganizer) ? trueCondition() : TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now()))
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
        int rowsDeleted = context.delete(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();

        if (rowsDeleted > 0) {
            return new Response(HttpStatus.OK.name(), ResponseCode.DELETE_SUCCESS, null);
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

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.eq(organizerID).and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                        .and(EVENTS.EVENT_ID.ne(UUID.fromString(eventID.trim()))))
                .limit(4)
                .fetchMaps();

        return getEventTickets(eventRecord);
    }

    public List<Map<String, Object>> getAllProfileEvent(Integer profileID) {
        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME,
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
                            .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now()))
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

    public List<Map<String, Object>> getSuggestedEvents(Integer limit, Integer profileID, String lat, String lon) {
        Optional<List<String>> suggested = Optional.empty();
        if(profileID != null && !profileID.toString().isEmpty()){
            try{
                suggested = Optional.ofNullable(suggestionClient.getEventSuggestions(profileID));
            } catch (Exception e){
                log.error("Error while connecting to suggestion service: {}", e.getMessage());
            }
        }

        Condition condition = trueCondition();

        if(suggested.isPresent() && !suggested.get().isEmpty()){
            condition = condition.and(EVENTS.EVENT_ID.in(suggested.get()));
        }
        else {
            condition = condition.and(EVENTS.STATUS.eq("published"))
                    .and(EVENTS.END_TIME.gt(OffsetDateTime.now()));
        }

        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(EVENTS)
                .where(condition.and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 30000))
                .limit(limit)
                .fetchMaps();

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getLocalizePopularEvents(String lat, String lon) {
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(POPULAREVENTS).join(EVENTS).on(POPULAREVENTS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(EVENTS.VISIBILITY.eq("public")
                .and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 5000)
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

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.LOCATION)
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
        System.out.println(organizerIDs.toString());
        if (organizerIDs.isEmpty()) {
            return List.of();
        }

        var subquery = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.LOCATION,
                        rowNumber().over(partitionBy(EVENTS.ORGANIZER_ID).orderBy(EVENTS.START_TIME.asc())).as("row_num")
                )
                .from(EVENTS)
                .where(EVENTS.ORGANIZER_ID.in(organizerIDs).and(EVENTS.START_TIME.gt(OffsetDateTime.now())))
                .asTable("foo");

        var eventRecord = context.select(subquery.field(EVENTS.EVENT_ID), subquery.field(EVENTS.NAME),
                        subquery.field(EVENTS.IMAGES), subquery.field(EVENTS.START_TIME), subquery.field(EVENTS.LOCATION)
                )
                .from(subquery)
                .where(field("row_num").lessOrEqual(2))
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

        return context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.START_TIME, EVENTS.IMAGES, EVENTS.LOCATION, EVENTS.END_TIME,
                        ORDERS.ORDER_ID, ORDERS.CREATED_AT)
                .from(ORDERS)
                .join(EVENTS).on(ORDERS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(ORDERS.PROFILE_ID.eq(profileID).and(ORDERS.STATUS.eq("paid")).and(condition))
                .orderBy(ORDERS.CREATED_AT.desc())
                .limit(10)
                .fetchMaps();
    }

    public List<Map<String, Object>> getEventRecord(Condition condition, String lat, String lon, Table<?> view, Boolean isOnline, String query){
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        condition = condition.and(EVENTS.STATUS.eq("published")).and(EVENTS.START_TIME.gt(OffsetDateTime.now()));

        if(isOnline){
            condition = condition.and(jsonbGetAttribute(EVENTS.LOCATION, "locationType").equalIgnoreCase("online"));
        } else {
            condition = condition.and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 30000);
        }

        Field<Double> rankField = DSL.val(0.0);
        if(query != null && !query.isEmpty()){
            rankField = DSL.field("ts_rank(search_vector, plainto_tsquery(?))", Double.class, query);
        }

        return context.select(
                        EVENTS.EVENT_ID,
                        EVENTS.NAME,
                        EVENTS.IMAGES,
                        EVENTS.START_TIME,
                        EVENTS.PROFILE_ID,
                        EVENTS.LOCATION,
                        rankField.as("rank")
                )
                .from(view)
                .where(condition)
                .orderBy(rankField.desc(), EVENTS.START_TIME)
                .limit(12)
                .fetchMaps();
    }

    public List<Map<String, Object>> getOnlineEvents(String lat, String lon) {
        var eventRecord = getEventRecord(
                trueCondition().and(trueCondition()), lat, lon, EVENTS, true, null);

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getSuggestedEventByTime(String lat, String lon, String timeType) {
        Condition condition = EventUtils.constructTimeCondition(timeType);

        var eventRecord = getEventRecord(condition, lat, lon, EVENTS, false, null);

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getSuggestedEventByType(String lat, String lon, String eventType) {
        var eventRecord = getEventRecord(EVENTTYPES.NAME.equalIgnoreCase(eventType), lat, lon
                , EVENTS.join(SUBCATEGORIES).on(EVENTS.SUB_CATEGORY_ID.eq(SUBCATEGORIES.SUB_CATEGORY_ID))
                        .join(CATEGORIES).on(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID))
                        .join(EVENTTYPES).on(EVENTS.EVENT_TYPE_ID.eq(EVENTTYPES.EVENT_TYPE_ID)), false, null);
        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getSuggestedEventsByCost(String lat, String lon, Double cost) {
        Condition condition = trueCondition();

        condition = condition.and(TICKETTYPES.STATUS.eq("visible")
            .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                    .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
            .or(TICKETTYPES.STATUS.eq("custom")
                    .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                    .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now()))));

        if(cost == 0.0){
            condition = condition.and(TICKETTYPES.TICKET_TYPE.equalIgnoreCase("free"));
        }
        else{
            condition = condition.and(TICKETTYPES.PRICE.lessOrEqual(BigDecimal.valueOf(cost)));
        }

        var eventRecord = getEventRecord(condition, lat, lon,
                EVENTS.join(TICKETTYPES).on(EVENTS.EVENT_ID.eq(TICKETTYPES.EVENT_ID)), false, null);

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

    public Response updateRecurrenceEvent(String eventID, Integer timezone, List<RecurrenceDTO> data) {
        List<Integer> occurenceIDs = new LinkedList<>();
        context.deleteFrom(EVENTOCCURRENCES)
                .where(EVENTOCCURRENCES.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();
        context.update(EVENTS)
                .set(EVENTS.START_TIME, LocalDate.parse(data.getFirst().getStartDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .atTime(LocalTime.parse(data.getFirst().getStartTime(), DateTimeFormatter.ofPattern("HH:mm"))).atOffset(ZoneOffset.ofHours(timezone)))
                .set(EVENTS.END_TIME, LocalDate.parse(data.getLast().getStartDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .atTime(LocalTime.parse(data.getLast().getEndTime(), DateTimeFormatter.ofPattern("HH:mm"))).atOffset(ZoneOffset.ofHours(timezone)))
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();
        data.forEach(item -> {
            var id = context.insertInto(EVENTOCCURRENCES)
                    .set(EVENTOCCURRENCES.EVENT_ID, UUID.fromString(eventID))
                    .set(EVENTOCCURRENCES.START_DATE, LocalDate.parse(item.getStartDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .set(EVENTOCCURRENCES.START_TIME, LocalTime.parse(item.getStartTime(), DateTimeFormatter.ofPattern("HH:mm")))
                    .set(EVENTOCCURRENCES.END_TIME, LocalTime.parse(item.getEndTime(), DateTimeFormatter.ofPattern("HH:mm")))
                    .returningResult(EVENTOCCURRENCES.OCCURRENCE_ID)
                    .fetchOneInto(Integer.class);
            occurenceIDs.add(id);
        });
        return new Response(HttpStatus.OK.name(), "OK", occurenceIDs);
    }

    public Response deleteOccurrence(String date, String eventID) {
        context.deleteFrom(EVENTOCCURRENCES)
                .where(EVENTOCCURRENCES.EVENT_ID.eq(UUID.fromString(eventID))
                        .and(EVENTOCCURRENCES.START_DATE.eq(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")))))
                .execute();
        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    public Map<String, Object> getOnlineEventInfo(String eventID) {
        return context.select(
                        EVENTS.EVENT_ID, EVENTS.NAME, EVENTTYPES.NAME.as("event_type"), EVENTS.SHOW_END_TIME, EVENTS.SHORT_DESCRIPTION,
                        EVENTS.IMAGES, EVENTS.VIDEOS, EVENTS.START_TIME, EVENTS.END_TIME, EVENTS.LOCATION, EVENTS.CREATED_AT,
                        CATEGORIES.NAME.as("category"), SUBCATEGORIES.NAME.as("sub_category"), EVENTS.TAGS, EVENTS.STATUS, EVENTS.REFUND_POLICY,
                        EVENTS.FAQ, EVENTS.FULL_DESCRIPTION, EVENTS.CAPACITY, EVENTS.UPDATED_AT, EVENTS.LANGUAGE, EVENTS.IS_RECURRING, EVENTS.TIMEZONE, EVENTS.PROFILE_ID
                )
                .from(EVENTS).join(SUBCATEGORIES).on(EVENTS.SUB_CATEGORY_ID.eq(SUBCATEGORIES.SUB_CATEGORY_ID))
                .join(CATEGORIES).on(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID))
                .join(EVENTTYPES).on(EVENTTYPES.EVENT_TYPE_ID.eq(EVENTS.EVENT_TYPE_ID))
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneMap();
    }

    @Transactional
    public Response createEventWithAI(Integer userID, Integer profileID, Double price, Boolean isFree, EventDTO eventDTO) {
        final ZoneOffset offset = ZoneOffset.ofHours(Integer.parseInt(eventDTO.getTimezone()));
        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        final LocalDate eventDate = LocalDate.parse(eventDTO.getEventDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        final LocalTime startTime = LocalTime.parse(eventDTO.getEventStartTime(), timeFormatter);
        final LocalTime endTime = LocalTime.parse(eventDTO.getEventEndTime(), timeFormatter);

        final OffsetDateTime eventStartTime = eventDate.atTime(startTime).atOffset(offset);
        final OffsetDateTime eventEndTime = eventDate.atTime(endTime).atOffset(offset);

        final JSONB locationJsonb = EventUtils.constructLocation(eventDTO.getLocationType(), eventDTO.getLatitude(), eventDTO.getLongitude()
                , eventDTO.getLocation(), eventDTO.getLocationName());

        context.insertInto(EVENTS)
                .set(EVENTS.EVENT_ID, eventDTO.getEventID())
                .set(EVENTS.NAME, eventDTO.getTitle())
                .set(EVENTS.EVENT_TYPE_ID, context.select(EVENTTYPES.EVENT_TYPE_ID).from(EVENTTYPES)
                        .where(EVENTTYPES.NAME.equalIgnoreCase(eventDTO.getType())).fetchOneInto(Integer.class))
                .set(EVENTS.SUB_CATEGORY_ID, context.select(SUBCATEGORIES.SUB_CATEGORY_ID).from(SUBCATEGORIES)
                        .where(SUBCATEGORIES.NAME.eq(eventDTO.getSubCategory())).fetchOneInto(Integer.class))
                .set(EVENTS.SHORT_DESCRIPTION, eventDTO.getSummary())
                .set(EVENTS.FULL_DESCRIPTION, eventDTO.getAdditionalInfo())
                .set(EVENTS.START_TIME, eventStartTime)
                .set(EVENTS.END_TIME, eventEndTime)
                .set(EVENTS.LOCATION, locationJsonb)
                .set(EVENTS.RESERVE_SEATING, eventDTO.getReserveSeating())
                .set(EVENTS.CAPACITY, eventDTO.getCapacity())
                .set(EVENTS.IMAGES, eventDTO.getImages())
                .set(EVENTS.IS_RECURRING, false)
                .set(EVENTS.TIMEZONE, eventDTO.getTimezone())
                .set(EVENTS.ORGANIZER_ID, userID)
                .set(EVENTS.PROFILE_ID, profileID)
                .set(EVENTS.TAGS, eventDTO.getTags())
                .set(EVENTS.STATUS, "draft")
                .returningResult(EVENTS.EVENT_ID)
                .fetchOneInto(UUID.class);

        TicketDTO ticket = TicketDTO.builder()
                .ticketName("General Admission")
                .price(isFree ? "0" : price.toString())
                .ticketType(isFree ? "free" : "paid")
                .minPerOrder(1).maxPerOrder(1)
                .quantity(eventDTO.getCapacity())
                .startDate(eventStartTime.minusMonths(1).isAfter(OffsetDateTime.now()) ? eventDTO.getEventStartTime()
                        : OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .startTime("00:00")
                .endDate(eventEndTime.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .endTime("23:59")
                .visibility("visible")
                .build();

        ticketService.addTicket(eventDTO.getEventID().toString(), ticket, Integer.parseInt(eventDTO.getTimezone()), false);

        return new Response(HttpStatus.OK.name(), "OK", eventDTO.getEventID());
    }

    public List<Map<String, Object>> getEventsByMapBounds(String northEastLat, String northEastLon, String southWestLat, String southWestLon) {
        String envelope = "ST_MakeEnvelope(" + southWestLon + ", " + southWestLat + ", " + northEastLon + ", " + northEastLat + ", 4326)";

        var eventRecord = context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.IMAGES, EVENTS.START_TIME, EVENTS.PROFILE_ID,
                        EVENTS.LOCATION, EVENTS.REFUND_POLICY, EVENTS.FAQ)
                .from(EVENTS)
                .where("ST_Contains(" + envelope + ", coordinates::geometry)")
                .and(EVENTS.STATUS.eq("published"))
                .and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                .fetchMaps();

        return getEventTickets(getListOrganizerEvent(eventRecord));
    }

    public Response getOrganizerReport(Integer userID, Integer profileID, String startDate, String endDate) {
        Map<String, Object> report = new HashMap<>();

        Condition condition = EVENTS.ORGANIZER_ID.eq(userID);
        if (profileID != null) {
            condition = condition.and(EVENTS.PROFILE_ID.eq(profileID));
        }

        var userEvent = context.select(EVENTS.EVENT_ID)
                .from(EVENTS)
                .where(condition)
                .fetchInto(UUID.class);

        if (userEvent.isEmpty()) {
            return new Response(HttpStatus.NOT_FOUND.name(), "Nothing to report", null);
        }

        OffsetDateTime startTime = OffsetDateTime.parse(startDate);
        OffsetDateTime endTime = OffsetDateTime.parse(endDate);

        var grossRevenueData = context.select(
                        ORDERS.CREATED_AT.cast(SQLDataType.DATE).as("date"),
                        sum(PAYMENTS.AMOUNT).as("revenue"))
                .from(ORDERS)
                .join(PAYMENTS).on(ORDERS.PAYMENT_ID.eq(PAYMENTS.PAYMENT_ID))
                .where(ORDERS.EVENT_ID.in(userEvent)
                        .and(ORDERS.CREATED_AT.between(startTime, endTime)))
                .groupBy(ORDERS.CREATED_AT.cast(SQLDataType.DATE))
                .fetchMaps();
        report.put("grossRevenueData", grossRevenueData);

        var ticketsAndBuyersData = context.select(
                        ORDERS.CREATED_AT.cast(SQLDataType.DATE).as("date"),
                        sum(ORDERITEMS.QUANTITY).as("tickets"),
                        countDistinct(ORDERS.PROFILE_ID).as("buyers"))
                .from(ORDERS)
                .join(ORDERITEMS).on(ORDERS.ORDER_ID.eq(ORDERITEMS.ORDER_ID))
                .where(ORDERS.EVENT_ID.in(userEvent)
                        .and(ORDERS.CREATED_AT.between(startTime, endTime)))
                .groupBy(ORDERS.CREATED_AT.cast(SQLDataType.DATE))
                .fetchMaps();

        report.put("ticketsAndBuyersData", ticketsAndBuyersData);

        List<Map<String, Object>> viewsByEvent = context.select(EVENTVIEWS.EVENT_ID, EVENTS.NAME,
                        count(EVENTVIEWS.VIEW_ID).as("views"))
                .from(EVENTVIEWS).join(EVENTS).on(EVENTVIEWS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(EVENTVIEWS.EVENT_ID.in(userEvent)
                        .and(EVENTVIEWS.VIEW_DATE.between(startTime, endTime)))
                .groupBy(EVENTVIEWS.EVENT_ID, EVENTS.NAME)
                .fetchMaps();

        report.put("viewsByEvent", viewsByEvent);

        List<Map<String, Object>> likedByEvent = context.select(LIKEDEVENTS.EVENT_ID, EVENTS.NAME,
                        count(LIKEDEVENTS.LIKED_AT).as("likes"))
                .from(LIKEDEVENTS).join(EVENTS).on(LIKEDEVENTS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(LIKEDEVENTS.EVENT_ID.in(userEvent)
                        .and(LIKEDEVENTS.LIKED_AT.between(startTime, endTime)))
                .groupBy(LIKEDEVENTS.EVENT_ID, EVENTS.NAME)
                .fetchMaps();

        report.put("likedByEvent", likedByEvent);

        return new Response(HttpStatus.OK.name(), "OK", report);
    }

    public Response handleReportEvent(ReportDTO report, Integer timezone) {
        context.insertInto(EVENTREPORTS)
                .set(EVENTREPORTS.EVENT_ID, UUID.fromString(report.getEventID()))
                .set(EVENTREPORTS.REPORTER_EMAIL, report.getReporterEmail())
                .set(EVENTREPORTS.REPORTER_PROFILE_ID, report.getReporterProfileID())
                .set(EVENTREPORTS.REPORT_DETAILS, report.getDetail())
                .set(EVENTREPORTS.REPORT_REASON, report.getReason())
                .set(EVENTREPORTS.REPORT_DATE, OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.ofHours(timezone)))
                .execute();

        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    public Map<String, Object> loadEventInfo(String eventID) {
        Map<String, Object> data = new HashMap<>();

        var ticketSales = context.select(
                        TICKETTYPES.TICKET_TYPE_ID,
                        TICKETTYPES.TICKET_TYPE,
                        TICKETTYPES.NAME,
                        SEATTIERS.NAME.as("tier_name"), SEATTIERS.TIER_COLOR, SEATTIERS.PERKS,
                        TICKETTYPES.QUANTITY.as("total_quantity"),
                        TICKETTYPES.PRICE,
                        TICKETTYPES.CURRENCY,
                        TICKETTYPES.SALE_START_TIME,
                        TICKETTYPES.SALE_END_TIME,
                        sum(
                                case_()
                                        .when(ORDERS.STATUS.eq("paid"), ORDERITEMS.QUANTITY)
                                        .otherwise(0)
                        ).as("sold_quantity"),
                        sum(
                                case_()
                                        .when(ORDERS.STATUS.eq("paid"), ORDERITEMS.QUANTITY.mul(TICKETTYPES.PRICE))
                                        .otherwise(0)
                        ).as("total"))
                .from(TICKETTYPES)
                .leftJoin(SEATTIERS).on(TICKETTYPES.SEAT_TIER_ID.eq(SEATTIERS.SEAT_TIER_ID))
                .leftJoin(TICKETS).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                .leftJoin(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                .leftJoin(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID)))
                .groupBy(TICKETTYPES.TICKET_TYPE_ID, SEATTIERS.NAME, SEATTIERS.TIER_COLOR, SEATTIERS.PERKS,
                        TICKETTYPES.TICKET_TYPE, TICKETTYPES.NAME, TICKETTYPES.QUANTITY, TICKETTYPES.PRICE,
                        TICKETTYPES.CURRENCY, TICKETTYPES.SALE_START_TIME, TICKETTYPES.SALE_END_TIME)
                .fetchMaps();

        var totalViews = context.select(count(EVENTVIEWS.VIEW_ID))
                .from(EVENTVIEWS)
                .where(EVENTVIEWS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneInto(Integer.class);

        data.put("ticketSales", ticketSales);
        data.put("totalViews", totalViews);

        return data;
    }

    public List<Map<String, Object>> getVenueSeatMap(String eventID, Integer profileID) {
        var eventCoords = context.select(EVENTS.COORDINATES).from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchOneInto(String.class);

        if (eventCoords == null || profileID == null) {
            return List.of();
        }

        return context.select(SEATMAP.MAP_ID, SEATMAP.NAME, SEATMAP.MAP_URL, SEATMAP.CAPACITY,
                        SEATMAP.OWNER_ID, SEATMAP.CREATED_AT, SEATMAP.UPDATED_AT).from(SEATMAP)
                .where(SEATMAP.OWNER_ID.eq(profileID).and(SEATMAP.EVENT_ID.eq(UUID.fromString(eventID)))
                        .or(SEATMAP.IS_PUBLIC.isTrue().and(field("ST_DWithin(SEATMAP.COORDINATES::geography, ?::geography, 10)", Boolean.class, eventCoords)))
                )
                .fetchMaps();
    }

    public List<Map<String, Object>> getSeatMapTiers(Integer seatMapID) {
        return context.select(SEATTIERS.SEAT_TIER_ID, SEATTIERS.NAME, SEATTIERS.TIER_COLOR,
                        SEATTIERS.ASSIGNEDSEATS, SEATTIERS.PERKS)
                .from(SEATTIERS)
                .where(SEATTIERS.MAP_ID.eq(seatMapID))
                .fetchMaps();
    }

    public Response getSeatMapInfo(Integer mapID){
        var data = context.select(SEATMAP.EVENT_ID, SEATMAP.MAP_ID, SEATMAP.NAME, SEATMAP.MAP_URL, SEATMAP.CAPACITY, SEATMAP.IS_PUBLIC, SEATMAP.UPDATED_AT)
                .from(SEATMAP)
                .where(SEATMAP.MAP_ID.eq(mapID))
                .fetchOneMap();

        assert data != null;
        String eventName = context.select(EVENTS.NAME).from(EVENTS)
                .where(EVENTS.EVENT_ID.eq((UUID) data.get("event_id")))
                .fetchOneInto(String.class);

        List<Integer> tierIDs = context.select(SEATTIERS.SEAT_TIER_ID).from(SEATTIERS)
                .where(SEATTIERS.MAP_ID.eq(mapID))
                .fetchInto(Integer.class);

        data.put("eventName", eventName);
        data.put("tierIDs", tierIDs);

        return new Response(HttpStatus.OK.name(), "OK", data);
    }

    public Response updateSeatMap(String mapID, SeatMapDTO data) {
        context.update(SEATMAP)
                .set(SEATMAP.NAME, data.getName())
                .set(SEATMAP.MAP_URL, data.getMapURL())
                .set(SEATMAP.CAPACITY, data.getCapacity())
                .set(SEATMAP.IS_PUBLIC, data.getIsPublic())
                .set(SEATMAP.UPDATED_AT, OffsetDateTime.now())
                .where(SEATMAP.MAP_ID.eq(Integer.parseInt(mapID)))
                .execute();

        List<Integer> tierIDs = data.getTiers().stream()
                .map(tierData -> {
                    if (tierData.getTierID() == null) {
                        return context.insertInto(SEATTIERS)
                                .set(SEATTIERS.MAP_ID, Integer.parseInt(mapID))
                                .set(SEATTIERS.NAME, tierData.getName())
                                .set(SEATTIERS.TIER_COLOR, tierData.getColor())
                                .set(SEATTIERS.ASSIGNEDSEATS, tierData.getTotalAssignedSeats())
                                .set(SEATTIERS.PERKS, tierData.getPerks())
                                .returningResult(SEATTIERS.SEAT_TIER_ID)
                                .fetchOneInto(Integer.class);
                    } else {
                        context.update(SEATTIERS)
                                .set(SEATTIERS.NAME, tierData.getName())
                                .set(SEATTIERS.TIER_COLOR, tierData.getColor())
                                .set(SEATTIERS.ASSIGNEDSEATS, tierData.getTotalAssignedSeats())
                                .set(SEATTIERS.PERKS, tierData.getPerks())
                                .where(SEATTIERS.SEAT_TIER_ID.eq(Integer.parseInt(tierData.getTierID())))
                                .execute();
                        return Integer.parseInt(tierData.getTierID());
                    }
                })
                .collect(Collectors.toList());

        return new Response(HttpStatus.OK.name(), "OK", tierIDs);
    }

    public Response deleteTier(Integer seatMapID, Integer tierID) {
        context.deleteFrom(SEATTIERS)
                .where(SEATTIERS.MAP_ID.eq(seatMapID).and(SEATTIERS.SEAT_TIER_ID.eq(tierID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "OK", null);
    }

    public List<Map<String, Object>> loadEventAttendees(String eventID) {
        List<Integer> attendeeIDs = context.selectDistinct(ATTENDEES.PROFILE_ID)
                .from(ATTENDEES)
                .join(TICKETS).on(ATTENDEES.TICKET_ID.eq(TICKETS.TICKET_ID))
                .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                .where(ATTENDEES.EVENT_ID.eq(UUID.fromString(eventID))
                        .and(ORDERS.STATUS.eq("paid")))
                .fetchInto(Integer.class);

        if (attendeeIDs.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> attendeeInfoList = accountClient.getEventAttendeeInfo(attendeeIDs);

        Map<Integer, Map<String, Object>> attendeeInfoMap = new HashMap<>();
        for (Map<String, Object> info : attendeeInfoList) {
            Integer profileId = (Integer) info.get("profile_id");
            if (profileId != null) {
                attendeeInfoMap.put(profileId, info);
            }
        }

        var attendees = context.select(
                        ATTENDEES.PROFILE_ID,
                        TICKETTYPES.TICKET_TYPE_ID,
                        TICKETTYPES.NAME.as("ticket_name"),
                        count().as("ticket_count"),
                        max(ATTENDEES.REGISTRATION_DATE).as("registration_date"))
                .from(ATTENDEES)
                .join(TICKETS).on(ATTENDEES.TICKET_ID.eq(TICKETS.TICKET_ID))
                .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                .where(ATTENDEES.EVENT_ID.eq(UUID.fromString(eventID))
                        .and(ORDERS.STATUS.eq("paid")))
                .groupBy(ATTENDEES.PROFILE_ID, TICKETTYPES.TICKET_TYPE_ID, TICKETTYPES.NAME)
                .fetchMaps();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> attendee : attendees) {
            Integer profileID = (Integer) attendee.get("profile_id");
            Map<String, Object> attendeeInfo = attendeeInfoMap.get(profileID);

            if (attendeeInfo != null) {
                Map<String, Object> combined = new HashMap<>(attendee);
                combined.put("profileName", attendeeInfo.get("profile_name"));
                combined.put("fullName", attendeeInfo.get("full_name"));
                combined.put("phoneNumber", attendeeInfo.get("phone_number"));
                combined.put("email", attendeeInfo.get("account_email"));
                result.add(combined);
            }
        }

        return result;
    }

    public Response sendAttendeesEmail(AttendeeEmailDTO emailDTO) {
        List<CompletableFuture<Void>> futures = emailDTO.getRecipients().stream()
                .map(recipient -> CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendEmail(recipient, emailDTO.getContent(), emailDTO.getSubject());
                    } catch (Exception e) {
                        log.error("Error sending to {}: {}", recipient, e.getMessage());
                    }
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return new Response(HttpStatus.OK.name(), "Email sent successfully", null);
    }
}
