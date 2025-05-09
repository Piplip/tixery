package com.nkd.event.service;

import com.nkd.event.dto.*;
import com.nkd.event.tables.records.DiscountcodesRecord;
import com.nkd.event.utils.CommonUtils;
import com.nkd.event.utils.EventUtils;
import com.nkd.event.utils.ResponseCode;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.nkd.event.Tables.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class TicketService {

    private final DSLContext context;
    private final TemplateEngine templateEngine;
    private final RedisTemplate<String, Object> redisTemplate;

    public Response addTicket(String eventID, TicketDTO ticket, Integer timezone, Boolean isRecurring) {
        Integer ticketID = saveTicket(eventID, ticket, timezone, isRecurring);
        return new Response(HttpStatus.OK.name(), "OK", ticketID);
    }

    private Integer saveTicket(String eventID, TicketDTO ticket, Integer timezone, Boolean isRecurring){
        var salesTime = EventUtils.transformDate(ticket.getStartDate(), ticket.getEndDate(),
                ticket.getStartTime(), ticket.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;

        if(ticket.getVisibility().equalsIgnoreCase("custom")){
            visDuration = EventUtils.transformDate(ticket.getVisibleStartDate(), ticket.getVisibleEndDate(),
                    ticket.getVisibleStartTime(), ticket.getVisibleEndTime(), timezone);
        }

        String currency = ticket.getCurrency() != null ? ticket.getCurrency() : "USD";
        String currencySymbol = ticket.getCurrencySymbol() != null ? ticket.getCurrencySymbol() : "$";
        String currencyFullForm = ticket.getCurrencyFullForm() != null ? ticket.getCurrencyFullForm() : "United States Dollar";

        JSONB currencyData = JSONB.jsonb(
                """
                {
                    "currency": "%s",
                    "symbol": "%s",
                    "fullForm": "%s"
                }
                """.formatted(currency, currencySymbol, currencyFullForm)
        );

        var ticketID = context.insertInto(TICKETTYPES)
                .set(TICKETTYPES.EVENT_ID, UUID.fromString(eventID))
                .set(TICKETTYPES.TICKET_TYPE, ticket.getTicketType())
                .set(TICKETTYPES.NAME, ticket.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticket.getQuantity())
                .set(TICKETTYPES.AVAILABLE_QUANTITY, ticket.getQuantity())
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
                .set(TICKETTYPES.SEAT_TIER_ID, Integer.parseInt(ticket.getTierID()))
                .set(TICKETTYPES.CURRENCY, currencyData)
                .returningResult(TICKETTYPES.TICKET_TYPE_ID)
                .fetchOneInto(Integer.class);

        if (isRecurring && ticket.getOccurrence() != null && !ticket.getOccurrence().isEmpty()) {
            var bulkInsert = context.insertInto(
                    TICKETTYPEOCCURRENCES,
                    TICKETTYPEOCCURRENCES.TICKET_TYPE_ID,
                    TICKETTYPEOCCURRENCES.OCCURRENCE_ID
            );

            for (Integer occurrenceId : ticket.getOccurrence()) {
                bulkInsert = bulkInsert.values(ticketID, occurrenceId);
            }

            bulkInsert.execute();
        }

        return ticketID;
    }

    public Response updateTicket(Integer ticketID, TicketDTO ticketDTO, Integer timezone) {
        var salesTime = EventUtils.transformDate(ticketDTO.getStartDate(), ticketDTO.getEndDate(),
                ticketDTO.getStartTime(), ticketDTO.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;

        if (ticketDTO.getVisibility().equalsIgnoreCase("custom")) {
            visDuration = EventUtils.transformDate(ticketDTO.getVisibleStartDate(), ticketDTO.getVisibleEndDate(),
                    ticketDTO.getVisibleStartTime(), ticketDTO.getVisibleEndTime(), timezone);
        }

        String currency = ticketDTO.getCurrency() != null ? ticketDTO.getCurrency() : "USD";
        String currencySymbol = ticketDTO.getCurrencySymbol() != null ? ticketDTO.getCurrencySymbol() : "$";
        String currencyFullForm = ticketDTO.getCurrencyFullForm() != null ? ticketDTO.getCurrencyFullForm() : "United States Dollar";

        JSONB currencyData = JSONB.jsonb(
                """
                {
                    "currency": "%s",
                    "symbol": "%s",
                    "fullForm": "%s"
                }
                """.formatted(currency, currencySymbol, currencyFullForm)
        );

        int rowsUpdated = context.update(TICKETTYPES)
                .set(TICKETTYPES.TICKET_TYPE, ticketDTO.getTicketType())
                .set(TICKETTYPES.NAME, ticketDTO.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticketDTO.getQuantity())
                .set(TICKETTYPES.AVAILABLE_QUANTITY, ticketDTO.getQuantity())
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
                .set(TICKETTYPES.CURRENCY, currencyData)
                .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticketID))
                .execute();

        if (rowsUpdated > 0) {
            return new Response(HttpStatus.OK.name(), "Ticket updated successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Ticket not found", null);
        }
    }

    @Transactional
    public Response deleteTicket(Integer ticketID, Boolean isRecurring) {
        int rowsDeleted = context.deleteFrom(TICKETTYPES)
                .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticketID))
                .execute();

        if(isRecurring){
            context.deleteFrom(TICKETTYPEOCCURRENCES)
                    .where(TICKETTYPEOCCURRENCES.TICKET_TYPE_ID.eq(ticketID))
                    .execute();
        }

        if (rowsDeleted > 0) {
            return new Response(HttpStatus.OK.name(), "Ticket deleted successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Ticket not found", null);
        }
    }

    @Async("taskExecutor")
    @Transactional
    protected void generateTickets(Integer orderID, List<TicketDTO> tickets, String eventID, Integer userID, Integer profileID,
                                   Boolean isReserve, List<String> ticketTierIDs) {
        var insertOrderItems = context.insertInto(ORDERITEMS, ORDERITEMS.ORDER_ID, ORDERITEMS.QUANTITY, ORDERITEMS.PRICE);

        for (TicketDTO ticket : tickets) {
            insertOrderItems = insertOrderItems.values(orderID, ticket.getQuantity(), new BigDecimal(ticket.getPrice()));
        }

        List<Integer> orderItemIDs = insertOrderItems.returningResult(ORDERITEMS.ORDER_ITEM_ID).fetch()
                .getValues(ORDERITEMS.ORDER_ITEM_ID);

        List<Integer> generatedTicketIDs;
        UUID eventUUID = UUID.fromString(eventID);

        if (!isReserve) {
            var insertTickets = context.insertInto(
                    TICKETS, TICKETS.EVENT_ID, TICKETS.TICKET_TYPE_ID, TICKETS.ORDER_ITEM_ID,
                    TICKETS.USER_ID, TICKETS.PROFILE_ID, TICKETS.PURCHASE_DATE, TICKETS.STATUS);

            for (int i = 0; i < tickets.size(); i++) {
                TicketDTO ticket = tickets.get(i);
                Integer orderItemID = orderItemIDs.get(i);

                insertTickets = insertTickets.values(eventUUID, ticket.getTicketTypeID(), orderItemID, userID, profileID, OffsetDateTime.now(), "sold");
            }

            generatedTicketIDs = insertTickets.returningResult(TICKETS.TICKET_ID).fetch().getValues(TICKETS.TICKET_ID);
        }
        else {
            generatedTicketIDs = new ArrayList<>();
            for (String ticketTierID : ticketTierIDs) {
                TicketDTO ticket = tickets.stream()
                        .filter(t -> t.getTicketTypeID() != null)
                        .findFirst()
                        .orElse(tickets.getFirst());

                Integer orderItemID = orderItemIDs.getFirst();

                Integer ticketId = context.update(TICKETS)
                        .set(TICKETS.ORDER_ITEM_ID, orderItemID)
                        .set(TICKETS.EVENT_ID, eventUUID)
                        .set(TICKETS.USER_ID, userID)
                        .set(TICKETS.PROFILE_ID, profileID)
                        .set(TICKETS.PURCHASE_DATE, OffsetDateTime.now())
                        .set(TICKETS.TICKET_TYPE_ID, ticket.getTicketTypeID())
                        .set(TICKETS.STATUS, "reserved")
                        .where(TICKETS.SEAT_IDENTIFIER.eq(ticketTierID))
                        .returningResult(TICKETS.TICKET_ID)
                        .fetchOneInto(Integer.class);

                generatedTicketIDs.add(ticketId);
            }        }

        var insertAttendees = context.insertInto(ATTENDEES, ATTENDEES.EVENT_ID, ATTENDEES.USER_ID, ATTENDEES.PROFILE_ID, ATTENDEES.TICKET_ID);

        for (int i = 0; i < tickets.size(); i++) {
            insertAttendees = insertAttendees.values(eventUUID, userID, profileID, generatedTicketIDs.get(i));
        }

        insertAttendees.execute();

        for (TicketDTO ticket : tickets) {
            context.update(TICKETTYPES)
                    .set(TICKETTYPES.AVAILABLE_QUANTITY, TICKETTYPES.AVAILABLE_QUANTITY.minus(ticket.getQuantity()))
                    .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticket.getTicketTypeID()))
                    .execute();
        }
    }

    public List<Map<String, Object>> getOrderTicket(Integer orderID) {
        return context.select(TICKETTYPES.NAME, ORDERITEMS.PRICE, ORDERITEMS.QUANTITY, TICKETS.TICKET_ID, TICKETS.PURCHASE_DATE, EVENTS.ORGANIZER_ID,
                        SEATTIERS.NAME.as("tier_name"), SEATTIERS.PERKS, SEATMAP.MAP_URL, SEATTIERS.TIER_COLOR, TICKETS.SEAT_IDENTIFIER, EVENTS.REFUND_POLICY, PAYMENTS.CURRENCY, EVENTS.END_TIME)
                .from(ORDERS.join(ORDERITEMS).on(ORDERS.ORDER_ID.eq(ORDERITEMS.ORDER_ID))
                        .join(TICKETS).on(ORDERITEMS.ORDER_ITEM_ID.eq(TICKETS.ORDER_ITEM_ID))
                        .join(TICKETTYPES).on(TICKETS.TICKET_TYPE_ID.eq(TICKETTYPES.TICKET_TYPE_ID))
                        .leftJoin(SEATTIERS).on(SEATTIERS.SEAT_TIER_ID.eq(TICKETTYPES.SEAT_TIER_ID))
                        .leftJoin(SEATMAP).on(SEATTIERS.MAP_ID.eq(SEATMAP.MAP_ID))
                        .join(EVENTS).on(TICKETS.EVENT_ID.eq(EVENTS.EVENT_ID))
                        .leftJoin(PAYMENTS).on(ORDERS.PAYMENT_ID.eq(PAYMENTS.PAYMENT_ID)))
                .where(ORDERS.ORDER_ID.eq(orderID))
                .fetchMaps();
    }

    @Async("taskExecutor")
    protected void cleanUpOnDeleteOrder(Integer orderID, Integer profileID) {
        context.update(TICKETTYPES)
                .set(TICKETTYPES.AVAILABLE_QUANTITY, TICKETTYPES.QUANTITY)
                .where(TICKETTYPES.TICKET_TYPE_ID.in(
                        context.select(TICKETS.TICKET_TYPE_ID)
                                .from(TICKETS).join(ORDERITEMS).on(TICKETS.ORDER_ITEM_ID.eq(ORDERITEMS.ORDER_ITEM_ID))
                                .where(ORDERITEMS.ORDER_ID.eq(orderID))
                ))
                .execute();
        context.deleteFrom(ATTENDEES)
                .where(ATTENDEES.PROFILE_ID.eq(profileID))
                .execute();
    }

    public ResponseEntity<?> downloadTicket(PrintTicketDTO ticket) {
        try {
            Context context = new Context();
            context.setVariable("ticket", ticket);
            context.setVariable("eventImg", CommonUtils.convertUrlToDataUri(ticket.getEventImg()));
            byte[] qrBytes = EventUtils.generateTicketQRCode(ticket.getTicketID(), ticket.getEventID(), ticket.getOrderID());
            String qrDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
            context.setVariable("qrCode", qrDataUri);
            context.setVariable("printDate", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            String htmlContent = templateEngine.process("print_ticket", context);

            byte[] pdfBytes = getPdfBytes(htmlContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("ticket.pdf").build());
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error generating ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private byte @NotNull [] getPdfBytes(String htmlContent) throws IOException {
        ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(htmlContent, new ClassPathResource("templates/").getURL().toString());
        builder.toStream(pdfStream);
        builder.useFont(new File("D:\\Web projects\\microservices\\tixery-be\\event-service\\src\\main\\resources\\templates\\Nunito-VariableFont_wght.ttf"), "Nunito");
        builder.run();

        return pdfStream.toByteArray();
    }

    public Response activateCoupon(Integer profileID, List<CouponDTO> coupon) {
        coupon.forEach(item -> context.insertInto(DISCOUNTCODES)
                .set(DISCOUNTCODES.ORGANIZER_PROFILE_ID, profileID)
                .set(DISCOUNTCODES.CODE, item.getCode())
                .set(DISCOUNTCODES.DISCOUNT_TYPE, item.getType())
                .set(DISCOUNTCODES.DISCOUNT_AMOUNT, BigDecimal.valueOf(item.getDiscount()))
                .set(DISCOUNTCODES.QUANTITY, item.getQuantity())
                .set(DISCOUNTCODES.VALID_FROM, OffsetDateTime.parse(item.getValidFrom()))
                .set(DISCOUNTCODES.VALID_TO, OffsetDateTime.parse(item.getValidTo()))
                .execute());

        return new Response(HttpStatus.OK.name(), ResponseCode.COUPON_ACTIVATED, null);
    }

    public Response handleCoupon(String coupon, Integer profileID) {
        DiscountcodesRecord record = context.selectFrom(DISCOUNTCODES)
                .where(DISCOUNTCODES.CODE.eq(coupon))
                .fetchOne();

        if(record == null) {
            return new Response(HttpStatus.NOT_FOUND.name(), ResponseCode.COUPON_NOT_FOUND, null);
        }

        if(record.getValidFrom().isAfter(OffsetDateTime.now()) || record.getValidTo().isBefore(OffsetDateTime.now())) {
            return new Response(HttpStatus.BAD_REQUEST.name(), ResponseCode.COUPON_EXPIRED, null);
        }

        if(record.getQuantity() == 0) {
            return new Response(HttpStatus.BAD_REQUEST.name(), ResponseCode.COUPON_RAN_OUT, null);
        }

        redisTemplate.opsForValue().set("coupon-" + profileID, coupon, 10, TimeUnit.MINUTES);
        var data = Map.of("type", record.getDiscountType(), "amount", record.getDiscountAmount());
        return new Response(HttpStatus.OK.name(), "Coupon applied successfully", data);
    }

    public Response addTierTicket(String eventID, TicketDTO ticketDTO, Integer timezone) {
        context.deleteFrom(TICKETTYPES)
                .where(TICKETTYPES.EVENT_ID.eq(UUID.fromString(eventID)))
                .execute();

        List<TicketDTO> tierTickets = ticketDTO.getTierData().stream().map(tier -> {
            TicketDTO ticket = new TicketDTO();
            ticket.setTierID(tier.getTierID());
            ticket.setTicketName(ticketDTO.getTicketName());
            ticket.setPrice(tier.getPrice());
            ticket.setTicketType("paid");
            ticket.setQuantity(tier.getTotalAssignedSeats());
            ticket.setStartDate(ticketDTO.getStartDate());
            ticket.setStartTime(ticketDTO.getStartTime());
            ticket.setEndDate(ticketDTO.getEndDate());
            ticket.setEndTime(ticketDTO.getEndTime());
            ticket.setDescription(ticketDTO.getDescription());
            ticket.setVisibility(ticketDTO.getVisibility());
            ticket.setVisibleEndDate(ticketDTO.getVisibleEndDate());
            ticket.setVisibleEndTime(ticketDTO.getVisibleEndTime());
            ticket.setVisibleStartDate(ticketDTO.getVisibleStartDate());
            ticket.setVisibleStartTime(ticketDTO.getVisibleStartTime());
            ticket.setMinPerOrder(ticketDTO.getMinPerOrder());
            ticket.setMaxPerOrder(ticketDTO.getMaxPerOrder());
            ticket.setCurrency(tier.getCurrency());
            ticket.setCurrencySymbol(tier.getCurrencySymbol());
            ticket.setCurrencyFullForm(tier.getCurrencyFullForm());
            return ticket;
        }).toList();

        List<Integer> ticketIDs = tierTickets.stream().map(ticket -> saveTicket(eventID, ticket, timezone, false)).toList();

        return new Response(HttpStatus.OK.name(), "OK", ticketIDs);
    }

    public Response updateTierTicket(String eventID, TicketTier ticketTier, Integer timezone){
        context.deleteFrom(TICKETTYPES)
                .where(TICKETTYPES.SEAT_TIER_ID.in(ticketTier.getTicketIDs()))
                .execute();

        return addTierTicket(eventID, ticketTier.getTicketData(), timezone);
    }

    public List<Map<String, Object>> getTierTicket(String eventID) {
        return context.select(TICKETS.STATUS, TICKETS.SEAT_IDENTIFIER)
                .from(TICKETS)
                .where(TICKETS.EVENT_ID.eq(UUID.fromString(eventID)))
                .fetchMaps();
    }
}
