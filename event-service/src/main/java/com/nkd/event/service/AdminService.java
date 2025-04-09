package com.nkd.event.service;

import com.nkd.event.client.AccountClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
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
import static com.nkd.event.tables.Eventreports.EVENTREPORTS;
import static com.nkd.event.tables.Events.EVENTS;
import static com.nkd.event.tables.Eventtypes.EVENTTYPES;
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
                            DSL.count(DSL.when(TICKETS.STATUS.eq("reserved"), 1)).as("sold_tickets")
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
                    .orderBy(EVENTS.START_TIME.asc())
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
                        .and(TICKETS.STATUS.eq("reserved"))
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

    public Map<String, Object> getOverviewMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime thirtyDaysAgo = now.minusDays(30);
            OffsetDateTime sixtyDaysAgo = now.minusDays(60);

            List<Map<String, Object>> ticketSoldDaily = context.select(
                            DSL.function("date", SQLDataType.DATE, TICKETS.CREATED_AT).as("date"),
                            DSL.count().as("count")
                    )
                    .from(TICKETS)
                    .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                    .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                    .where(TICKETS.CREATED_AT.greaterOrEqual(thirtyDaysAgo))
                    .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                    .and(ORDERS.STATUS.eq("paid"))
                    .groupBy(DSL.function("date", SQLDataType.DATE, TICKETS.CREATED_AT))
                    .orderBy(field("date"))
                    .fetchMaps();

            List<Integer> dailyTicketCounts = new ArrayList<>(Collections.nCopies(30, 0));
            long totalTicketsSold = 0;

            for (Map<String, Object> dayData : ticketSoldDaily) {
                LocalDate date = ((java.sql.Date) dayData.get("date")).toLocalDate();
                int daysAgo = (int) java.time.temporal.ChronoUnit.DAYS.between(date, now.toLocalDate());
                if (daysAgo >= 0 && daysAgo < 30) {
                    int count = ((Number) dayData.get("count")).intValue();
                    dailyTicketCounts.set(29 - daysAgo, count);
                    totalTicketsSold += count;
                }
            }

            Record2<Integer, Integer> eventCounts = context.select(
                            DSL.count().as("total"),
                            DSL.count(
                                    DSL.when(
                                            EVENTS.END_TIME.greaterThan(now)
                                                    .and(EVENTS.STATUS.notIn("cancelled", "deleted"))
                                                    .and(
                                                            DSL.exists(
                                                                    DSL.selectOne()
                                                                            .from(TICKETTYPES)
                                                                            .where(TICKETTYPES.EVENT_ID.eq(EVENTS.EVENT_ID))
                                                                            .and(TICKETTYPES.SALE_START_TIME.lessThan(now))
                                                                            .and(TICKETTYPES.SALE_END_TIME.greaterThan(now))
                                                            )
                                                    ),
                                            1)
                            ).as("active")
                    )
                    .from(EVENTS)
                    .fetchOne();

            assert eventCounts != null;
            int totalEvents = eventCounts.value1();

            Integer eventsLastMonth = context.selectCount()
                    .from(EVENTS)
                    .where(EVENTS.CREATED_AT.between(thirtyDaysAgo, now))
                    .fetchOneInto(Integer.class);

            Integer eventsPrevMonth = context.selectCount()
                    .from(EVENTS)
                    .where(EVENTS.CREATED_AT.between(sixtyDaysAgo, thirtyDaysAgo))
                    .fetchOneInto(Integer.class);

            double eventTrendPercentage = 0;
            String eventTrendDirection = "stable";
            if (eventsPrevMonth != null && eventsPrevMonth > 0 && eventsLastMonth != null) {
                eventTrendPercentage = ((double) eventsLastMonth - eventsPrevMonth) / eventsPrevMonth * 100;
                eventTrendDirection = eventTrendPercentage >= 0 ? "up" : "down";
                eventTrendPercentage = Math.abs(eventTrendPercentage);
            }

            int firstHalfTickets = 0;
            int secondHalfTickets = 0;
            for (int i = 0; i < 15; i++) {
                firstHalfTickets += dailyTicketCounts.get(i);
            }
            for (int i = 15; i < 30; i++) {
                secondHalfTickets += dailyTicketCounts.get(i);
            }
            double ticketTrendPercentage = 0;
            String ticketTrendDirection = "stable";
            if (firstHalfTickets > 0) {
                ticketTrendPercentage = ((double) secondHalfTickets - firstHalfTickets) / firstHalfTickets * 100;
                ticketTrendDirection = ticketTrendPercentage >= 0 ? "up" : "down";
                ticketTrendPercentage = Math.abs(ticketTrendPercentage);
            }

            var eventTypes = context.select(
                            EVENTTYPES.EVENT_TYPE_ID,
                            EVENTTYPES.NAME.as("label")
                    )
                    .from(EVENTTYPES)
                    .orderBy(EVENTTYPES.NAME)
                    .fetchMaps();

            OffsetDateTime sixMonthsAgo = now.minusMonths(6);
            OffsetDateTime twelveMonthsAgo = now.minusMonths(12);

            BigDecimal currentSixMonthRevenue = context.select(DSL.sum(TICKETTYPES.PRICE))
                    .from(TICKETS)
                    .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                    .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                    .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                    .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                    .where(TICKETS.CREATED_AT.between(sixMonthsAgo, now))
                    .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                    .and(ORDERS.STATUS.eq("paid"))
                    .fetchOneInto(BigDecimal.class);

            BigDecimal previousSixMonthRevenue = context.select(DSL.sum(TICKETTYPES.PRICE))
                    .from(TICKETS)
                    .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                    .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                    .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                    .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                    .where(TICKETS.CREATED_AT.between(twelveMonthsAgo, sixMonthsAgo))
                    .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                    .and(ORDERS.STATUS.eq("paid"))
                    .fetchOneInto(BigDecimal.class);

            currentSixMonthRevenue = (currentSixMonthRevenue != null) ? currentSixMonthRevenue : BigDecimal.ZERO;
            previousSixMonthRevenue = (previousSixMonthRevenue != null) ? previousSixMonthRevenue : BigDecimal.ZERO;

            double revenueTrendPercentage = 0;
            String revenueTrendDirection = "stable";

            if (previousSixMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = currentSixMonthRevenue.subtract(previousSixMonthRevenue);
                revenueTrendPercentage = change.divide(previousSixMonthRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100))
                        .doubleValue();
                revenueTrendDirection = revenueTrendPercentage >= 0 ? "up" : "down";
                revenueTrendPercentage = Math.abs(revenueTrendPercentage);
            }

            List<Map<String, Object>> formattedEventTypes = new ArrayList<>();
            for (Map<String, Object> eventType : eventTypes) {
                Integer eventTypeId = ((Number) eventType.get("event_type_id")).intValue();
                String eventTypeName = (String) eventType.get("label");

                List<BigDecimal> monthlyRevenue = new ArrayList<>();
                for (int i = 5; i >= 0; i--) {
                    OffsetDateTime monthStart = now.minusMonths(i + 1).withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    OffsetDateTime monthEnd;

                    if (i == 0) {
                        monthEnd = now;
                    } else {
                        int lastDayOfMonth = now.minusMonths(i).getMonth().length(now.minusMonths(i).toLocalDate().isLeapYear());
                        monthEnd = now.minusMonths(i).withDayOfMonth(lastDayOfMonth).withHour(23).withMinute(59).withSecond(59);
                    }

                    BigDecimal revenue = context.select(DSL.sum(TICKETTYPES.PRICE))
                            .from(TICKETS)
                            .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                            .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                            .join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                            .join(ORDERS).on(ORDERITEMS.ORDER_ID.eq(ORDERS.ORDER_ID))
                            .where(TICKETS.CREATED_AT.between(monthStart, monthEnd))
                            .and(EVENTS.EVENT_TYPE_ID.eq(eventTypeId))
                            .and(TICKETS.STATUS.in("reserved", "transferred", "active"))
                            .and(ORDERS.STATUS.eq("paid"))
                            .fetchOneInto(BigDecimal.class);

                    monthlyRevenue.add(revenue != null ? revenue : BigDecimal.ZERO);
                }
                Map<String, Object> eventTypeData = new HashMap<>();
                eventTypeData.put("id", eventTypeName.toLowerCase().replace(" ", "_"));
                eventTypeData.put("label", eventTypeName);
                eventTypeData.put("data", monthlyRevenue);
                formattedEventTypes.add(eventTypeData);
            }

            Map<String, Object> ticketsSoldData = new HashMap<>();
            ticketsSoldData.put("title", "Tickets Sold");
            ticketsSoldData.put("value", formatLargeNumber(totalTicketsSold));
            ticketsSoldData.put("interval", 30);
            ticketsSoldData.put("trend", ticketTrendDirection);
            ticketsSoldData.put("trendPercentage", Math.round(ticketTrendPercentage * 10) / 10.0); // Round to 1 decimal place
            ticketsSoldData.put("data", dailyTicketCounts);

            List<Map<String, Object>> eventStartDaily = context.select(
                            DSL.function("date", SQLDataType.DATE, EVENTS.START_TIME).as("date"),
                            DSL.count().as("count")
                    )
                    .from(EVENTS)
                    .where(EVENTS.START_TIME.greaterOrEqual(thirtyDaysAgo.plusDays(1)))
                    .groupBy(DSL.function("date", SQLDataType.DATE, EVENTS.START_TIME))
                    .orderBy(field("date"))
                    .fetchMaps();

            List<Integer> dailyEventCounts = new ArrayList<>(Collections.nCopies(30, 0));

            for (Map<String, Object> dayData : eventStartDaily) {
                LocalDate date = ((java.sql.Date) dayData.get("date")).toLocalDate();
                int daysAgo = (int) java.time.temporal.ChronoUnit.DAYS.between(date, now.toLocalDate());
                if (daysAgo >= 0 && daysAgo < 30) {
                    int count = ((Number) dayData.get("count")).intValue();
                    dailyEventCounts.set(29 - daysAgo, count);
                }
            }

            Map<String, Object> eventsData = new HashMap<>();
            eventsData.put("title", "Total Events");
            eventsData.put("dailyEvents", dailyEventCounts);
            eventsData.put("value", formatLargeNumber(totalEvents));
            int totalDailyEvents = dailyEventCounts.stream().mapToInt(Integer::intValue).sum();
            eventsData.put("interval", totalDailyEvents);
            eventsData.put("trend", eventTrendDirection);
            eventsData.put("trendPercentage", Math.round(eventTrendPercentage * 10) / 10.0); //

            Map<String, Object> revenueTrendData = new HashMap<>();
            revenueTrendData.put("currentRevenue", currentSixMonthRevenue);
            revenueTrendData.put("previousRevenue", previousSixMonthRevenue);
            revenueTrendData.put("trend", revenueTrendDirection);
            revenueTrendData.put("trendPercentage", Math.round(revenueTrendPercentage * 10) / 10.0);

            metrics.put("ticketsSold", ticketsSoldData);
            metrics.put("totalEvents", eventsData);
            metrics.put("topEventTypes", formattedEventTypes);
            metrics.put("revenueTrend", revenueTrendData);

        } catch (Exception e) {
            log.error("Error retrieving overview metrics", e);
            metrics.put("error", "Failed to retrieve metrics: " + e.getMessage());
        }

        return metrics;
    }

    public Map<String, Object> getEventReports(Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        try {
            int pageNum = Math.max(1, page == null ? 1 : page);
            int pageSize = Math.max(10, size == null ? 10 : size);
            int offset = (pageNum - 1) * pageSize;

            Map<UUID, Integer> reportCountByEvent = context.select(
                            EVENTREPORTS.EVENT_ID,
                            DSL.count().as("report_count")
                    )
                    .from(EVENTREPORTS)
                    .groupBy(EVENTREPORTS.EVENT_ID)
                    .fetchMap(EVENTREPORTS.EVENT_ID, field("report_count", Integer.class));

            var reports = context.select(
                            EVENTREPORTS.REPORT_ID,
                            EVENTREPORTS.EVENT_ID,
                            EVENTS.NAME.as("event_name"),
                            EVENTREPORTS.REPORTER_PROFILE_ID,
                            EVENTREPORTS.REPORTER_EMAIL,
                            EVENTREPORTS.REPORT_REASON,
                            EVENTREPORTS.REPORT_DETAILS,
                            EVENTREPORTS.REPORT_DATE,
                            EVENTREPORTS.STATUS
                    )
                    .from(EVENTREPORTS)
                    .join(EVENTS).on(EVENTREPORTS.EVENT_ID.eq(EVENTS.EVENT_ID))
                    .orderBy(EVENTREPORTS.REPORT_DATE.desc())
                    .limit(pageSize)
                    .offset(offset)
                    .fetchMaps();

            Integer totalCount = context.selectCount()
                    .from(EVENTREPORTS)
                    .fetchOneInto(Integer.class);

            var statusCounts = context.select(
                            EVENTREPORTS.STATUS,
                            DSL.count().as("count")
                    )
                    .from(EVENTREPORTS)
                    .groupBy(EVENTREPORTS.STATUS)
                    .fetchMap(EVENTREPORTS.STATUS, field("count", Integer.class));

            Set<Integer> reporterIds = reports.stream()
                    .map(r -> (Integer) r.get("reporter_profile_id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Integer, String> reporterNames = new HashMap<>();
            if (!reporterIds.isEmpty()) {
                String reporterIdsStr = reporterIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

                try {
                    reporterNames = accountClient.getListProfileName(reporterIdsStr);
                } catch (Exception e) {
                    log.warn("Failed to retrieve reporter names: {}", e.getMessage());
                }
            }

            for (Map<String, Object> report : reports) {
                Integer reporterId = (Integer) report.get("reporter_profile_id");
                if (reporterId != null) {
                    report.put("reporter_name", reporterNames.getOrDefault(reporterId, "Unknown"));
                }

                UUID eventId = (UUID) report.get("event_id");
                if (eventId != null) {
                    int reportCount = reportCountByEvent.getOrDefault(eventId, 1);
                    String reportLevel;

                    if (reportCount >= 10) {
                        reportLevel = "CRITICAL";
                    } else if (reportCount >= 5) {
                        reportLevel = "HIGH";
                    } else if (reportCount >= 3) {
                        reportLevel = "MEDIUM";
                    } else {
                        reportLevel = "LOW";
                    }

                    report.put("report_level", reportLevel);
                    report.put("report_count", reportCount);
                }
            }

            result.put("reports", reports);
            result.put("total", totalCount != null ? totalCount : 0);
            result.put("page", pageNum);
            result.put("size", pageSize);
            result.put("status_distribution", statusCounts);

        } catch (Exception e) {
            log.error("Error retrieving event reports", e);
            result.put("error", "Failed to retrieve event reports: " + e.getMessage());
        }

        return result;
    }

    private String formatLargeNumber(long number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fk", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }
}
