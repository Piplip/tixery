package com.nkd.event.utils;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.jooq.Condition;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.data.util.Pair;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.nkd.event.Tables.EVENTS;

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

    public static byte[] generateTicketQRCode(Integer ticketID, String eventID, Integer orderID){
        String qrData = String.format(
                "{\"event_id\":\"%s\",\"order_id\":\"%s\",\"ticket_id\":\"%s\"}",
                eventID, orderID, ticketID);
        ByteArrayOutputStream qrStream = QRCode.from(qrData)
                .withSize(150, 150)
                .to(ImageType.PNG)
                .stream();

        return qrStream.toByteArray();
    }

    public static Condition constructTimeCondition(String time){
        Condition condition = DSL.trueCondition();

        OffsetDateTime currentTime = OffsetDateTime.now();
        OffsetDateTime startTime = null;
        OffsetDateTime endTime = null;

        switch (time) {
            case "today" -> {
                startTime = currentTime.withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.withHour(23).withMinute(59).withSecond(59);
            }
            case "tomorrow" -> {
                startTime = currentTime.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusDays(1).withHour(23).withMinute(59).withSecond(59);
            }
            case "weekend" -> {
                startTime = currentTime.withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusDays(2).withHour(23).withMinute(59).withSecond(59);
            }
            case "week" -> {
                startTime = currentTime.withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusDays(7).withHour(23).withMinute(59).withSecond(59);
            }
            case "nextWeek" -> {
                startTime = currentTime.plusDays(7).withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusDays(14).withHour(23).withMinute(59).withSecond(59);
            }
            case "thisMonth" -> {
                startTime = currentTime.withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusMonths(1).withHour(23).withMinute(59).withSecond(59);
            }
            case "nextMonth" -> {
                startTime = currentTime.plusMonths(1).withHour(0).withMinute(0).withSecond(0);
                endTime = currentTime.plusMonths(2).withHour(23).withMinute(59).withSecond(59);
            }
        }

        return condition.and(EVENTS.START_TIME.between(startTime, endTime));
    }

    public static JSONB constructLocation(String locationType, String lat, String lon, String location, String locationName){
        return locationType.equals("venue") ?
                JSONB.jsonb("""
                    {
                        "location": "%s",
                        "locationType": "%s",
                        "lat": %s,
                        "lon": %s,
                        "name": "%s"
                    }
                """.formatted(
                        Optional.ofNullable(location).orElse("").replace("\r", "\\r").replace("\n", "\\n"),
                        Optional.of(locationType).orElse("").replace("\r", "\\r").replace("\n", "\\n"),
                        Optional.ofNullable(lat).orElse("0.0"),
                        Optional.ofNullable(lon).orElse("0.0"),
                        Optional.ofNullable(locationName).orElse("").replace("\r", "\\r").replace("\n", "\\n")
                ))
                :
                JSONB.jsonb("""
                                {
                                    "locationType": "online",
                                    "enabled": "true",
                                    "access": "true"
                                }
                                """);
    }
}
