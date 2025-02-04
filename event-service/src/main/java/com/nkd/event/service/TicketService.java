package com.nkd.event.service;

import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.utils.EventUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.nkd.event.Tables.TICKETTYPES;

@RequiredArgsConstructor
@Service
public class TicketService {

    private final DSLContext context;

    public Response addTicket(String eventID, TicketDTO ticket, Integer timezone) {
        var salesTime = EventUtils.transformDate(ticket.getStartDate(), ticket.getEndDate(),
                ticket.getStartTime(), ticket.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;

        if(ticket.getVisibility().equalsIgnoreCase("custom")){
            visDuration = EventUtils.transformDate(ticket.getVisibleStartDate(), ticket.getVisibleEndDate(),
                    ticket.getVisibleStartTime(), ticket.getVisibleEndTime(), timezone);
        }

        JSONB currencyData = JSONB.jsonb(
                """
                {
                    "currency": "%s",
                    "symbol": "%s",
                    "fullForm": "%s"
                }
                """.formatted(ticket.getCurrency(), ticket.getCurrencySymbol(), ticket.getCurrencyFullForm())
        );

        var ticketID = context.insertInto(TICKETTYPES)
                .set(TICKETTYPES.EVENT_ID, UUID.fromString(eventID))
                .set(TICKETTYPES.TICKET_TYPE, ticket.getTicketType())
                .set(TICKETTYPES.NAME, ticket.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticket.getQuantity())
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
                .set(TICKETTYPES.CURRENCY, currencyData)
                .returningResult(TICKETTYPES.TICKET_TYPE_ID)
                .fetchOneInto(Integer.class);

        return new Response(HttpStatus.OK.name(), "OK", ticketID);
    }

    public Response updateTicket(Integer ticketID, TicketDTO ticketDTO, Integer timezone) {
        var salesTime = EventUtils.transformDate(ticketDTO.getStartDate(), ticketDTO.getEndDate(),
                ticketDTO.getStartTime(), ticketDTO.getEndTime(), timezone);

        Pair<OffsetDateTime, OffsetDateTime> visDuration = null;

        if (ticketDTO.getVisibility().equalsIgnoreCase("custom")) {
            visDuration = EventUtils.transformDate(ticketDTO.getVisibleStartDate(), ticketDTO.getVisibleEndDate(),
                    ticketDTO.getVisibleStartTime(), ticketDTO.getVisibleEndTime(), timezone);
        }

        JSONB currencyData = JSONB.jsonb(
                """
                {
                    "currency": "%s",
                    "symbol": "%s",
                    "fullForm": "%s"
                }
                """.formatted(ticketDTO.getCurrency(), ticketDTO.getCurrencySymbol(), ticketDTO.getCurrencyFullForm())
        );

        int rowsUpdated = context.update(TICKETTYPES)
                .set(TICKETTYPES.TICKET_TYPE, ticketDTO.getTicketType())
                .set(TICKETTYPES.NAME, ticketDTO.getTicketName())
                .set(TICKETTYPES.QUANTITY, ticketDTO.getQuantity())
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
    public Response deleteTicket(Integer ticketID) {
        int rowsDeleted = context.deleteFrom(TICKETTYPES)
                .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticketID))
                .execute();

        if (rowsDeleted > 0) {
            return new Response(HttpStatus.OK.name(), "Ticket deleted successfully", null);
        } else {
            return new Response(HttpStatus.NOT_FOUND.name(), "Ticket not found", null);
        }
    }
}
