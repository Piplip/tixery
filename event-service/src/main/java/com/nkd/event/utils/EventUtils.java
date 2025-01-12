package com.nkd.event.utils;

import org.springframework.data.util.Pair;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class EventUtils {

    static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public static Pair<OffsetDateTime, OffsetDateTime> transformDate(String startDate, String endDate
            , String startTime, String endTime, Integer timezone) {
        LocalDate parsedStartDate = LocalDate.parse(startDate, dateFormatter);
        LocalDate parsedEndDate = LocalDate.parse(endDate, dateFormatter);
        LocalTime parsedStartTime = LocalTime.parse(startTime, timeFormatter);
        LocalTime parsedEndTime = LocalTime.parse(endTime, timeFormatter);

        final ZoneOffset offset = ZoneOffset.ofHours(timezone);

        final OffsetDateTime eventStartTime = parsedStartDate.atTime(parsedStartTime).atOffset(offset);
        final OffsetDateTime eventEndTime = parsedEndDate.atTime(parsedEndTime).atOffset(offset);

        return Pair.of(eventStartTime, eventEndTime);
    }
    
    public static OffsetDateTime convertToOffsetDateTime(String date, String time, Integer timezone) {
        LocalDate parsedDate = LocalDate.parse(date, dateFormatter);
        LocalTime parsedTime = LocalTime.parse(time, timeFormatter);
    
        final ZoneOffset offset = ZoneOffset.ofHours(timezone);
        return parsedDate.atTime(parsedTime).atOffset(offset);
    }
}
