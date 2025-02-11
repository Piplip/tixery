package com.nkd.event.service;

import com.nkd.event.dto.Response;
import com.nkd.event.utils.EventUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nkd.event.Tables.*;
import static org.jooq.impl.DSL.jsonbGetAttribute;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final DSLContext context;
    private final EventService eventService;

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
            condition = condition.and(EVENTS.EVENT_TYPE.eq("online"));
        } else if (type == 2) {
            condition = condition.and("st_dwithin(coordinates, st_geographyfromtext(?), ?)", userLocationPoint, 1000);
        }

        return context.select(EVENTS.EVENT_ID, EVENTS.NAME, EVENTS.SHORT_DESCRIPTION, EVENTS.TAGS, EVENTS.LOCATION, EVENTS.CATEGORY)
                .from(EVENTS)
                .where(condition)
                .limit(10)
                .fetchMaps();
    }

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

    public List<Map<String, Object>> getEventSearch(String eventIDs, String query, String categories, String lat, String lon,
                                                    String time, String price, Boolean online, Boolean isFollowOnly, List<Integer> followList) {
        Table<?> view = EVENTS;
        Condition condition = DSL.trueCondition();
        if(eventIDs != null && !eventIDs.isEmpty()){
            List<UUID> eventIDList = Stream.of(eventIDs.split(","))
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            condition = condition.and(EVENTS.EVENT_ID.in(eventIDList));
        }
        else {
            if(query != null && !query.isEmpty()){
                condition = condition.and(EVENTS.NAME.like("%" + query + "%")
                        .or(DSL.condition("search_vector @@ to_tsquery(?)", query))
                        .or(EVENTS.TAGS.like("%" + query + "%"))
                        .or(EVENTS.LOCATION.like("%" + query + "%")
                        .or(jsonbGetAttribute(EVENTS.LOCATION, "location").like(query))));
            }
            if(categories != null && !categories.isEmpty()){
                List<String> categoryList = Stream.of(categories.split(",")).collect(Collectors.toList());
                condition = condition.and(EVENTS.CATEGORY.in(categoryList));
            }
            if(time != null && !time.isEmpty()){
                condition = condition.and(EventUtils.constructTimeCondition(time));
            }
            if(price != null && !price.isEmpty()){
                condition = price.equals("free") ? condition.and(TICKETTYPES.TICKET_TYPE.equalIgnoreCase("free"))
                        : condition.and(TICKETTYPES.PRICE.greaterThan(BigDecimal.valueOf(0.0))
                        .and(TICKETTYPES.STATUS.eq("visible")
                                .or(TICKETTYPES.STATUS.eq("hid-on-sales").and(TICKETTYPES.SALE_START_TIME.lt(OffsetDateTime.now()))
                                        .and(TICKETTYPES.SALE_END_TIME.gt(OffsetDateTime.now())))
                                .or(TICKETTYPES.STATUS.eq("custom")
                                        .and(TICKETTYPES.VIS_START_TIME.lt(OffsetDateTime.now()))
                                        .and(TICKETTYPES.VIS_END_TIME.gt(OffsetDateTime.now())))));
                view = view.join(TICKETTYPES).on(EVENTS.EVENT_ID.eq(TICKETTYPES.EVENT_ID));
            }
            if(Optional.ofNullable(online).orElse(false)){
                condition = condition.and(EVENTS.EVENT_TYPE.eq("online"));
            }
            if(Optional.ofNullable(isFollowOnly).orElse(false)){
                condition = condition.and(EVENTS.ORGANIZER_ID.in(followList));
            }
        }
        var eventRecord = eventService.getEventRecord(condition, lat, lon, view);

        return eventService.getEventTickets(eventService.getListOrganizerEvent(eventRecord));
    }

    @Transactional
    public Response deleteSearchHistory(Integer searchID) {
        context.deleteFrom(SEARCHHISTORY)
                .where(SEARCHHISTORY.SEARCH_ID.eq(searchID))
                .execute();
        return new Response(HttpStatus.OK.name(), "Search history deleted successfully", null);
    }
}
