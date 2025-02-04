package com.nkd.event.service;

import com.nkd.event.dto.Response;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nkd.event.Tables.EVENTS;
import static com.nkd.event.Tables.SEARCHHISTORY;
import static org.jooq.impl.DSL.jsonbGetAttribute;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final DSLContext context;

    public List<Map<String, Object>> getEventSearchSuggestions(String query, Integer type, String lat, String lon, Integer userID) {
        String userLocationPoint = "POINT(" + lon + " " + lat + ")";

        context.insertInto(SEARCHHISTORY).set(SEARCHHISTORY.SEARCH_TERM, query)
                .set(SEARCHHISTORY.SEARCH_LOCATION, DSL.field("ST_GeographyFromText(?)", String.class, userLocationPoint))
                .set(SEARCHHISTORY.SEARCH_TIMESTAMP, OffsetDateTime.now())
                .set(SEARCHHISTORY.USER_ID, userID)
                .execute();

        Condition condition = DSL.condition("search_vector @@ to_tsquery(?)", query)
                .and(EVENTS.START_TIME.gt(OffsetDateTime.now()))
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

    @Transactional
    public Response deleteSearchHistory(Integer searchID) {
        context.deleteFrom(SEARCHHISTORY)
                .where(SEARCHHISTORY.SEARCH_ID.eq(searchID))
                .execute();
        return new Response(HttpStatus.OK.name(), "Search history deleted successfully", null);
    }
}
