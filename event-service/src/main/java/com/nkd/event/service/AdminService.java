package com.nkd.event.service;

import com.nkd.event.Tables;
import com.nkd.event.client.AccountClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.nkd.event.Tables.ORDERITEMS;
import static com.nkd.event.tables.Events.EVENTS;
import static com.nkd.event.tables.Orders.ORDERS;
import static com.nkd.event.tables.Tickets.TICKETS;
import static com.nkd.event.tables.Tickettypes.TICKETTYPES;
import static org.jooq.impl.DSL.field;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final DSLContext context;
    private final AccountClient accountClient;

    public Map<String, Object> getEventStats(String startDate, String endDate) {
        Map<String, Object> stats = new HashMap<>();

        try {
            OffsetDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            OffsetDateTime endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

            var ticketCounts = context.select(
                            DSL.count().as("total_tickets"),
                            DSL.count(DSL.when(TICKETS.STATUS.in("reserved", "transferred", "active"), 1)).as("sold_tickets")
                    )
                    .from(TICKETS)
                    .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                    .where(EVENTS.START_TIME.between(startDateTime, endDateTime))
                    .fetchOne();

            assert ticketCounts != null;
            Integer totalTickets = ticketCounts.get("total_tickets", Integer.class);
            Integer soldTickets = ticketCounts.get("sold_tickets", Integer.class);

            BigDecimal totalRevenue = context
                    .select(DSL.sum(TICKETTYPES.PRICE))
                    .from(TICKETS)
                    .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                    .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                    .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                    .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                    .where(EVENTS.START_TIME.between(startDateTime, endDateTime))
                    .and(ORDERS.STATUS.eq("paid"))
                    .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                    .fetchOne(0, BigDecimal.class);

            if (totalRevenue == null) {
                totalRevenue = BigDecimal.ZERO;
            }

            BigDecimal platformFee = totalRevenue.multiply(new BigDecimal("0.0215"))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal completionRate = BigDecimal.ZERO;
            if (totalTickets != null && totalTickets > 0 && soldTickets != null) {
                completionRate = new BigDecimal(soldTickets)
                        .divide(new BigDecimal(totalTickets), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            stats.put("totalRevenue", totalRevenue);
            stats.put("soldTickets", soldTickets != null ? soldTickets : 0);
            stats.put("platformFee", platformFee);
            stats.put("completionRate", completionRate);

        } catch (Exception e) {
            stats.put("error", "Failed to retrieve event statistics: " + e.getMessage());
            log.error("Error retrieving event statistics", e);
        }

        return stats;
    }

    public Map<String, Object> getEvents(String startDate, String endDate, Integer page, Integer size) {
        List<Map<String, Object>> eventList = new ArrayList<>();
        Map<String, Object> response = new HashMap<>();

        try {
            int pageNum = Math.max(1, page == null ? 1 : page);
            int pageSize = Math.max(1, size == null ? 10 : size);
            int offset = (pageNum - 1) * pageSize;

            OffsetDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            OffsetDateTime endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

            Integer totalCount = context.selectCount()
                    .from(EVENTS)
                    .where(EVENTS.START_TIME.between(startDateTime, endDateTime))
                    .fetchOneInto(Integer.class);

            var events = context.select(
                            EVENTS.EVENT_ID,
                            EVENTS.NAME,
                            EVENTS.ORGANIZER_ID,
                            EVENTS.START_TIME,
                            EVENTS.STATUS
                    )
                    .from(EVENTS)
                    .where(EVENTS.START_TIME.between(startDateTime, endDateTime))
                    .orderBy(EVENTS.START_TIME.desc())
                    .limit(pageSize)
                    .offset(offset)
                    .fetch();

            List<UUID> eventIds = events.stream()
                    .map(r -> r.get(EVENTS.EVENT_ID, UUID.class))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<Integer> organizerIds = events.stream()
                    .map(r -> r.get(EVENTS.ORGANIZER_ID, Integer.class))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            Map<Integer, String> organizerNames = new HashMap<>();
            if (!organizerIds.isEmpty()) {
                String organizerIdsStr = organizerIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

                if (!organizerIdsStr.isEmpty()) {
                    try {
                        Map<Integer, String> result = accountClient.getListProfileName(organizerIdsStr);
                        if (result != null) {
                            result.forEach((k, v) -> {
                                if (k != null) {
                                    organizerNames.put(k, v != null ? v : "Unknown");
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.warn("Failed to retrieve organizer names: {}", e.getMessage());
                    }
                }
            }

            if (!eventIds.isEmpty()) {
                Map<UUID, Record3<UUID, Integer, Integer>> ticketStatsByEvent = context.select(TICKETS.EVENT_ID, DSL.count().as("total_tickets"),
                                DSL.count(DSL.when(TICKETS.STATUS.eq("reserved"), 1)).as("sold_tickets")
                        )
                        .from(TICKETS)
                        .where(TICKETS.EVENT_ID.in(eventIds))
                        .groupBy(TICKETS.EVENT_ID)
                        .fetchMap(TICKETS.EVENT_ID);

                Map<UUID, BigDecimal> revenueByEvent = context
                        .select(
                                TICKETS.EVENT_ID,
                                DSL.sum(TICKETTYPES.PRICE).as("revenue")
                        )
                        .from(TICKETS)
                        .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                        .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                        .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                        .where(ORDERS.STATUS.eq("paid"))
                        .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                        .and(TICKETS.EVENT_ID.in(eventIds))
                        .groupBy(TICKETS.EVENT_ID)
                        .fetchMap(TICKETS.EVENT_ID, field("revenue", BigDecimal.class));

                for (Record eventRecord : events) {
                    UUID eventId = eventRecord.get(EVENTS.EVENT_ID);
                    if (eventId == null) continue;

                    Integer organizerId = eventRecord.get(EVENTS.ORGANIZER_ID, Integer.class);
                    Map<String, Object> eventMap = new HashMap<>();

                    eventMap.put("id", eventId);
                    eventMap.put("name", eventRecord.get(EVENTS.NAME));
                    eventMap.put("start_time", eventRecord.get(EVENTS.START_TIME));
                    eventMap.put("status", eventRecord.get(EVENTS.STATUS));

                    eventMap.put("organizer_name", organizerId != null ?
                            organizerNames.getOrDefault(organizerId, "Unknown") : "Unknown");

                    Record3<UUID, Integer, Integer> ticketStats = ticketStatsByEvent.get(eventId);
                    int totalTickets = 0;
                    int soldTickets = 0;

                    if (ticketStats != null) {
                        totalTickets = ticketStats.value2() != null ? ticketStats.value2() : 0;
                        soldTickets = ticketStats.value3() != null ? ticketStats.value3() : 0;
                    }

                    eventMap.put("total_tickets_sold", soldTickets);

                    BigDecimal completionRate = BigDecimal.ZERO;
                    if (totalTickets > 0) {
                        completionRate = new BigDecimal(soldTickets)
                                .divide(new BigDecimal(totalTickets), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                    eventMap.put("completion_rate", completionRate);

                    BigDecimal revenue = revenueByEvent.getOrDefault(eventId, BigDecimal.ZERO);
                    eventMap.put("revenue", revenue);

                    BigDecimal platformFee = revenue.multiply(new BigDecimal("0.0215"))
                            .setScale(2, RoundingMode.HALF_UP);
                    eventMap.put("platform_fee", platformFee);

                    eventList.add(eventMap);
                }
            }

            response.put("events", eventList);
            response.put("total_count", totalCount);
            response.put("page", pageNum);
            response.put("size", pageSize);

        } catch (DateTimeParseException e) {
            log.error("Invalid date format: {} or {}", startDate, endDate, e);
            response.put("error", "Invalid date format");
        } catch (Exception e) {
            log.error("Error retrieving events", e);
            response.put("error", "Error retrieving events: " + e.getMessage());
        }

        return response;
    }
}
