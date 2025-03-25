package com.nkd.event.service;

import com.nkd.event.dto.Response;
import com.nkd.event.dto.UserInteraction;
import com.nkd.event.utils.EventUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.nkd.event.Tables.*;
import static org.jooq.impl.DSL.jsonbGetAttribute;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final DSLContext context;
    private final EventService eventService;

    public List<Map<String, Object>> getLocalizeSearchTrends(String lat, String lon) {
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";
        return context.select(SEARCHHISTORY.SEARCH_TERM, DSL.count())
                .from(SEARCHHISTORY)
                .where("st_dwithin(search_location, st_geographyfromtext(?), ?)",userLocationPoint, 5000)
                .and(SEARCHHISTORY.SEARCH_TIMESTAMP.gt(OffsetDateTime.now().minusMonths(1)))
                .groupBy(SEARCHHISTORY.SEARCH_TERM)
                .orderBy(DSL.count().desc())
                .limit(10)
                .fetchMaps();
    }

    public List<Map<String, Object>> getSearchHistory(Integer userID) {
        var subquery = context.select(SEARCHHISTORY.SEARCH_ID, SEARCHHISTORY.SEARCH_TERM, SEARCHHISTORY.SEARCH_TIMESTAMP)
                .distinctOn(SEARCHHISTORY.SEARCH_TERM)
                .from(SEARCHHISTORY)
                .where(SEARCHHISTORY.USER_ID.eq(userID))
                .orderBy(SEARCHHISTORY.SEARCH_TERM, SEARCHHISTORY.SEARCH_TIMESTAMP.desc())
                .asTable("distinct_search_history");

        return context.select()
                .from(subquery)
                .orderBy(Objects.requireNonNull(subquery.field(SEARCHHISTORY.SEARCH_TIMESTAMP)).desc())
                .limit(10)
                .fetchMaps();
    }

    public List<Map<String, Object>> getEventSearch(String query, String category, String subCategory, String lat, String lon, String time,
                                                    String price, Boolean online, Boolean isFollowOnly, List<Integer> followList) {
        Table<?> view = EVENTS;
        Condition condition = DSL.trueCondition();

        if(query != null && !query.isEmpty()){
            condition = condition.and(EVENTS.NAME.like("%" + query + "%")
                    .or(DSL.condition("search_vector @@ to_tsquery(?)", query))
                    .or(EVENTS.TAGS.like("%" + query + "%"))
                    .or(EVENTS.LOCATION.like("%" + query + "%")
                    .or(jsonbGetAttribute(EVENTS.LOCATION, "location").like(query))));
        }
        if(category != null){
            condition = condition.and(CATEGORIES.NAME.like(category));

            if (subCategory != null) {
                condition = condition.and(SUBCATEGORIES.NAME.like(subCategory));
            } else {
                condition = condition.and(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID));
            }

            view = view.join(SUBCATEGORIES).on(EVENTS.SUB_CATEGORY_ID.eq(SUBCATEGORIES.SUB_CATEGORY_ID))
                    .join(CATEGORIES).on(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID));
        }
        if(time != null && !time.isEmpty()){
            condition = condition.and(EventUtils.constructTimeCondition(time));
        }
        if(price != null && !price.isEmpty()){
            condition = condition.and(TICKETTYPES.STATUS.eq("visible")
                    .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                            .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
                    .or(TICKETTYPES.STATUS.eq("custom")
                            .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                            .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now()))));
            if(price.equals("free")){
                condition = condition.and(TICKETTYPES.TICKET_TYPE.equalIgnoreCase("free"));
            }
            else{
                condition = condition.and(TICKETTYPES.PRICE.greaterThan(BigDecimal.valueOf(0.0)));
            }
            view = view.join(TICKETTYPES).on(EVENTS.EVENT_ID.eq(TICKETTYPES.EVENT_ID));
        }
        if(Optional.ofNullable(isFollowOnly).orElse(false)){
            condition = condition.and(EVENTS.PROFILE_ID.in(followList));
        }

        var eventRecord = eventService.getEventRecord(condition, lat, lon, view, Optional.ofNullable(online).orElse(false), query);

        return eventService.getEventTickets(eventService.getListOrganizerEvent(eventRecord));
    }

    public List<Map<String, Object>> getEventSearchSuggestions(String query, Integer type, String lat, String lon, Integer userID) {
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        context.insertInto(SEARCHHISTORY).set(SEARCHHISTORY.SEARCH_TERM, query)
                .set(SEARCHHISTORY.SEARCH_LOCATION, DSL.field("ST_GeographyFromText(?)", String.class, userLocationPoint))
                .set(SEARCHHISTORY.SEARCH_TIMESTAMP, OffsetDateTime.now())
                .set(SEARCHHISTORY.USER_ID, userID)
                .execute();

        Condition condition = DSL.condition("search_vector @@ to_tsquery(?)", query)
                .and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
                .or(EVENTS.TAGS.like("%" + query + "%"))
                .or(EVENTS.NAME.like("%" + query + "%"))
                .or(jsonbGetAttribute(EVENTS.LOCATION, "location").like(query));

        if(type == 1){
            condition = condition.and(String.valueOf(EVENTS.LOCATION.toString().contains("online")));
        } else if (type == 2) {
            condition = condition.and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 1000);
        }

        return context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.SHORT_DESCRIPTION, EVENTS.TAGS, EVENTS.LOCATION, CATEGORIES.NAME.as("category"))
                .from(EVENTS)
                .join(SUBCATEGORIES).on(EVENTS.SUB_CATEGORY_ID.eq(SUBCATEGORIES.SUB_CATEGORY_ID))
                .join(CATEGORIES).on(SUBCATEGORIES.CATEGORY_ID.eq(CATEGORIES.CATEGORY_ID))
                .where(condition)
                .limit(10)
                .fetchMaps();
    }

    @Transactional
    public Response deleteSearchHistory(Integer searchID) {
        context.deleteFrom(SEARCHHISTORY)
                .where(SEARCHHISTORY.SEARCH_ID.eq(searchID))
                .execute();
        return new Response(HttpStatus.OK.name(), "Search history deleted successfully", null);
    }

    // TODO: Adjust this function for reserver seating events
    public List<Map<String, Object>> loadOrders(String query, Integer range, Integer organizerID, String eventID) {
        Condition condition = DSL.trueCondition();

        if(!query.isEmpty()){
            try {
                int orderID = Integer.parseInt(query.trim());
                condition = condition.and(ORDERS.ORDER_ID.eq(orderID));
            } catch (NumberFormatException e) {
                condition = condition.and(EVENTS.NAME.like("%" + query + "%"));
            }
        }

        if(organizerID != null){
            condition = condition.and(EVENTS.ORGANIZER_ID.eq(organizerID));
        }

        if(eventID != null){
            condition = condition.and(EVENTS.EVENT_ID.eq(UUID.fromString(eventID)));
        }

        var orders = context.select(ORDERS.ORDER_ID, ORDERS.CREATED_AT, ORDERS.STATUS, PAYMENTS.CURRENCY, PAYMENTS.AMOUNT, PAYMENTS.PAYMENT_METHOD,
                        ORDERS.PROFILE_ID, EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.START_TIME, EVENTS.LOCATION)
                .from(ORDERS)
                .leftJoin(PAYMENTS).on(ORDERS.PAYMENT_ID.eq(PAYMENTS.PAYMENT_ID))
                .rightJoin(EVENTS).on(ORDERS.EVENT_ID.eq(EVENTS.EVENT_ID))
                .where(condition.and(ORDERS.CREATED_AT.gt(OffsetDateTime.now().minusMonths(range))))
                .fetchMaps();

        return orders.stream()
                .peek(order -> order.put("tickets", context.select(TICKETS.TICKET_ID, TICKETTYPES.NAME, ORDERITEMS.QUANTITY
                                , TICKETTYPES.PRICE, TICKETTYPES.CURRENCY, TICKETTYPES.TICKET_TYPE_ID)
                        .from(ORDERITEMS)
                                .join(TICKETS).on(ORDERITEMS.ORDER_ITEM_ID.eq(TICKETS.ORDER_ITEM_ID))
                                .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                        .where(ORDERITEMS.ORDER_ID.eq((Integer) order.get("order_id")))
                        .fetchMaps()))
                .collect(Collectors.toList());
    }

    public void trackUserInteraction(UserInteraction userInteraction) {
        context.insertInto(USERINTERACTIONS)
                .set(USERINTERACTIONS.PROFILE_ID, userInteraction.getProfileID())
                .set(USERINTERACTIONS.EVENT_ID, UUID.fromString(userInteraction.getEventID()))
                .set(USERINTERACTIONS.INTERACTION_TYPE, userInteraction.getType())
                .set(USERINTERACTIONS.INTERACTION_STRENGTH, userInteraction.getStrength())
                .set(USERINTERACTIONS.ORGANIZER_ID, userInteraction.getOrganizerID())
                .set(USERINTERACTIONS.TIMESTAMP, LocalDateTime.now())
                .execute();
    }
}
