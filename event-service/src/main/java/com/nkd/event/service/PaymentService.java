package com.nkd.event.service;

import com.nkd.event.dto.PaymentDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.StripeResponse;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.enumeration.EventOperationType;
import com.nkd.event.enumeration.PaymentStatus;
import com.nkd.event.event.EventOperation;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.nkd.event.Tables.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${stripe.secret-key}")
    private String secretKey;
    @Value("${client.port}")
    private String clientPort;

    private final DSLContext context;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, List<TicketDTO>> cache = new HashMap<>();

    private final TicketService ticketService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public StripeResponse handleStripeCheckout(Boolean isReserve, PaymentDTO paymentDTO) {
        Stripe.apiKey = secretKey;

       if(!isReserve){
           try {
               handleReserveTicket(paymentDTO.getTickets(), paymentDTO.getProfileID());
           } catch (Exception e) {
               log.error("Error checking ticket availability: {}", e.getMessage());
               return StripeResponse.builder().status("failed").message(e.getMessage()).build();
           }
       }
       else{
           Optional<String> unavailableTicket = paymentDTO.getTierTicketIDs().stream()
                   .filter(ticketID -> !context.fetchExists(
                           context.selectFrom(TICKETS)
                                   .where(TICKETS.SEAT_IDENTIFIER.eq(ticketID))
                                   .and(TICKETS.EVENT_ID.eq(UUID.fromString(paymentDTO.getEventID())))
                                   .and(TICKETS.STATUS.eq("available"))
                   ))
                   .findFirst();

           if (unavailableTicket.isPresent()) {
               return StripeResponse.builder()
                       .status("failed")
                       .message("Ticket not available for " + unavailableTicket.get())
                       .build();
           }
       }

        var orderID = context.insertInto(ORDERS)
                .set(ORDERS.USER_ID, paymentDTO.getUserID())
                .set(ORDERS.PROFILE_ID, paymentDTO.getProfileID())
                .set(ORDERS.EVENT_ID, UUID.fromString(paymentDTO.getEventID()))
                .set(ORDERS.STATUS, "pending")
                .returningResult(ORDERS.ORDER_ID)
                .fetchOneInto(Integer.class);

        redisTemplate.opsForValue().set("stripe-order-" + orderID, Map.of("eventID", paymentDTO.getEventID(), "reserve", isReserve,
                "tierTicketIDs", paymentDTO.getTierTicketIDs(),"userID", paymentDTO.getUserID(), "profileID", paymentDTO.getProfileID(),
                "amount", paymentDTO.getAmount(), "currency", paymentDTO.getCurrency(), "email", paymentDTO.getEmail(),
                "username", paymentDTO.getUsername()), 10, TimeUnit.MINUTES);
        cache.put("stripe-order-" + orderID, paymentDTO.getTickets());

        SessionCreateParams params = createStripeParams(paymentDTO, orderID, isReserve);
        var response = createStripeResponse(params);

        context.insertInto(PAYMENTS)
                .set(PAYMENTS.PAYMENT_ID, UUID.randomUUID())
                .set(PAYMENTS.ORDER_ID, orderID)
                .set(PAYMENTS.PAYMENT_METHOD, "stripe")
                .set(PAYMENTS.AMOUNT, BigDecimal.valueOf(paymentDTO.getAmount()))
                .set(PAYMENTS.CURRENCY, paymentDTO.getCurrency())
                .set(PAYMENTS.PAYMENT_STATUS, response.getStatus())
                .set(PAYMENTS.TRANSACTION_ID, response.getSessionID())
                .execute();

        return response;
    }

    @Transactional
    public StripeResponse handleSuccessfulStripePayment(Integer orderID, Integer profileID, Boolean isReserve) {
        Object value = redisTemplate.opsForValue().get("stripe-order-" + orderID);
        StripeResponse response = new StripeResponse();

        if(orderID == null) {
            return StripeResponse.builder().status("failed").message("Order ID is required").build();
        }

        if(!context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID))) {
            return StripeResponse.builder().status("failed").message("Order does not exist").build();
        }

        Optional<UUID> paymentID = context.update(PAYMENTS)
                .set(PAYMENTS.PAYMENT_STATUS, "success")
                .where(PAYMENTS.ORDER_ID.eq(orderID))
                .returningResult(PAYMENTS.PAYMENT_ID)
                .fetchOptionalInto(UUID.class);

        if(paymentID.isEmpty()) {
            return StripeResponse.builder().status("failed").message("Payment does not exist").build();
        }

        response.setSessionID(paymentID.get().toString());

        if(context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID).and(ORDERS.STATUS.eq("paid")))) {
            return StripeResponse.builder().status("failed").message("Order already paid")
                    .sessionID(paymentID.get().toString()).amount(0L).build();
        }

        context.update(ORDERS)
                .set(ORDERS.STATUS, "paid")
                .set(ORDERS.PAYMENT_ID, paymentID.get())
                .where(ORDERS.ORDER_ID.eq(orderID))
                .execute();

        if(value instanceof Map){
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> outerMap = (Map<String, Object>) value;
                response.setAmount(Long.parseLong(outerMap.get("amount").toString()));

                @SuppressWarnings("unchecked")
                List<String> ticketTierIDs = (List<String>) outerMap.get("tierTicketIDs");
                List<TicketDTO> tickets = cache.get("stripe-order-" + orderID);
                ticketService.generateTickets(orderID, tickets, outerMap.get("eventID").toString(), ((Integer) outerMap.get("userID")),
                        ((Integer) outerMap.get("profileID")), isReserve, ticketTierIDs);

                if(!isReserve){
                    cleanUpOnSuccessPayment(orderID, profileID);
                }
                else{
                    Integer mapID = context.select(SEATMAP.MAP_ID).from(SEATMAP)
                            .where(SEATMAP.EVENT_ID.eq(UUID.fromString(outerMap.get("eventID").toString())))
                            .fetchOneInto(Integer.class);
                    if(mapID == null) {
                        log.error("Seat map not found for event ID: {}", outerMap.get("eventID").toString());
                    }
                    String destination = "/seat-map/" + mapID;
                    messagingTemplate.convertAndSend(destination, ticketTierIDs);
                }

                response.setStatus("success");
                response.setMessage("Payment successful");
                response.setCurrency(outerMap.get("currency").toString());

                PaymentDTO payment = PaymentDTO.builder()
                        .tickets(tickets)
                        .eventID(outerMap.get("eventID").toString())
                        .userID((Integer) outerMap.get("userID"))
                        .profileID((Integer) outerMap.get("profileID"))
                        .amount(response.getAmount())
                        .currency(outerMap.get("currency").toString())
                        .email(outerMap.get("email").toString())
                        .username(outerMap.get("username").toString())
                        .build();

                publisher.publishEvent(payment);
            }
            catch (Exception e) {
                response.setStatus("failed");
                response.setMessage("Internal server error");
                log.error("Error getting tickets: {}", e.getMessage());
            }
        }

        return response;
    }

    public StripeResponse handleFailedStripePayment(Integer orderID, Integer profileID, PaymentStatus status) {
        cleanUpOnSuccessPayment(orderID, profileID);

        if(orderID == null) {
            return StripeResponse.builder().status("failed").message("Order ID is required").build();
        }

        if(!context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID))) {
            return StripeResponse.builder().status("failed").message("Order does not exist").build();
        }

        if(context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID).and(ORDERS.STATUS.eq("paid")))) {
            return StripeResponse.builder().status("failed").message("Order already paid").build();
        }

        var paymentID = context.update(PAYMENTS)
                .set(PAYMENTS.PAYMENT_STATUS, status.name())
                .where(PAYMENTS.ORDER_ID.eq(orderID))
                .returningResult(PAYMENTS.PAYMENT_ID)
                .fetchOneInto(UUID.class);

        var eventID = context.update(ORDERS)
                .set(ORDERS.STATUS, "cancelled")
                .set(ORDERS.CANCEL_REASON, status.getDescription())
                .where(ORDERS.ORDER_ID.eq(orderID))
                .returningResult(ORDERS.EVENT_ID)
                .fetchOneInto(UUID.class);

        assert paymentID != null;
        assert eventID != null;
        return StripeResponse.builder().status("failed").message("Payment cancelled")
                .sessionID(paymentID.toString())
                .eventID(eventID.toString())
                .paymentMethod("Stripe").build();
    }

    public Response cancelOrder(Integer orderID, String username, String email) {
        if(orderID == null) {
            log.error("Order ID is required");
            return new Response(HttpStatus.BAD_REQUEST.name(), "Unexpected error", null);
        }

        if(!context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID))) {
            log.error("Order does not exist");
            return new Response(HttpStatus.BAD_REQUEST.name(), "Order does not exist", null);
        }

        if(context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID).and(ORDERS.STATUS.eq("cancelled")))) {
            log.error("Order already cancelled");
            return new Response(HttpStatus.BAD_REQUEST.name(), "Order already cancelled", null);
        }

        OffsetDateTime eventDate = context.select(EVENTS.END_TIME)
                .from(EVENTS)
                .where(EVENTS.EVENT_ID.eq(context.select(ORDERS.EVENT_ID)
                        .from(ORDERS)
                        .where(ORDERS.ORDER_ID.eq(orderID))
                        .fetchOneInto(UUID.class)))
                .fetchOneInto(OffsetDateTime.class);

        if(eventDate != null && eventDate.isBefore(OffsetDateTime.now())) {
            log.error("Event has already passed");
            return new Response(HttpStatus.BAD_REQUEST.name(), "Event has already passed", null);
        }

        var paymentID = context.update(PAYMENTS)
                .set(PAYMENTS.PAYMENT_STATUS, PaymentStatus.USER_CANCELLED.name())
                .where(PAYMENTS.ORDER_ID.eq(orderID))
                .returningResult(PAYMENTS.PAYMENT_ID)
                .fetchOneInto(UUID.class);

        var eventID = context.update(ORDERS)
                .set(ORDERS.STATUS, "cancelled")
                .set(ORDERS.CANCEL_REASON, PaymentStatus.USER_CANCELLED.getDescription())
                .where(ORDERS.ORDER_ID.eq(orderID))
                .returningResult(ORDERS.EVENT_ID)
                .fetchOneInto(UUID.class);

        assert paymentID != null;
        assert eventID != null;
        ticketService.cleanUpOnDeleteOrder(orderID);

        EventOperation cancelEvent = EventOperation.builder()
                .data(Map.of("orderID", orderID, "username", username, "eventID", eventID, "email", email))
                .type(EventOperationType.CANCEL)
                .build();
        publisher.publishEvent(cancelEvent);

        return new Response(HttpStatus.OK.name(), "Order cancelled successfully", null);
    }

    private void handleReserveTicket(List<TicketDTO> tickets, Integer profileID){
        tickets.forEach(ticket -> {
            Integer availableQuantity = context.select(TICKETTYPES.AVAILABLE_QUANTITY)
                    .from(TICKETTYPES)
                    .where(TICKETTYPES.TICKET_TYPE_ID.eq(ticket.getTicketTypeID()))
                    .fetchOneInto(Integer.class);
            String totalKey = "total-" + ticket.getTicketTypeID();

            Integer totalReservedQuantity = (Integer) redisTemplate.opsForValue().get(totalKey);
            if (totalReservedQuantity == null) {
                totalReservedQuantity = 0;
            }

            if (availableQuantity == null || availableQuantity - totalReservedQuantity < ticket.getQuantity()) {
                throw new RuntimeException("Ticket not available for " + ticket.getTicketName() +
                        ". Please lower the quantity and try again");
            }

            redisTemplate.opsForValue().increment(totalKey, ticket.getQuantity());

            String profileKey = "reserved-for-" + profileID;
            redisTemplate.opsForValue().set(profileKey, ticket.getQuantity());
        });
    }

    private StripeResponse createStripeResponse(SessionCreateParams params) {
        Session session;

        try {
            session = Session.create(params);
        } catch (Exception e) {
            log.error("Error creating session: {}", e.getMessage());
            return StripeResponse.builder().status("failed").message(e.getMessage()).build();
        }

        return StripeResponse.builder()
                .status("success")
                .message("Session created successfully")
                .sessionID(session.getId())
                .sessionURL(session.getUrl())
                .build();
    }

    private SessionCreateParams createStripeParams(PaymentDTO paymentDTO, Integer orderID, Boolean isReserve) {
        SessionCreateParams.LineItem.PriceData.ProductData productData = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                .setName(paymentDTO.getName())
                .build();

        SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(Optional.ofNullable(paymentDTO.getCurrency()).orElse("USD"))
                .setUnitAmount(paymentDTO.getAmount())
                .setProductData(productData)
                .build();

        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(paymentDTO.getQuantity())
                .setPriceData(priceData)
                .build();

        String clientURL = "http://localhost:" + clientPort;

        String successURL = clientURL + "/payment/success" + "?orderID=" + orderID;
        String cancelURL = clientURL + "/payment/error" + "?orderID=" + orderID;

        if(isReserve){
            successURL = successURL + "&reserve=true";
            cancelURL = cancelURL + "&reserve=true";
        }

        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successURL)
                .setCancelUrl(cancelURL)
                .addLineItem(lineItem)
                .build();
    }

    private void cleanUpOnSuccessPayment(Integer orderID, Integer profileID) {
        redisTemplate.delete("stripe-order-" + orderID);
        redisTemplate.delete("reserved-for-" + profileID);

        cache.remove("stripe-order-" + orderID);

        String coupon = (String) redisTemplate.opsForValue().get("coupon-" + profileID);

        if(coupon == null) {
            return;
        }

        context.update(DISCOUNTCODES)
                .set(DISCOUNTCODES.QUANTITY, DISCOUNTCODES.QUANTITY.minus(1))
                .where(DISCOUNTCODES.CODE.eq(coupon))
                .execute();

        context.insertInto(APPLIEDDISCOUNTS)
                .set(APPLIEDDISCOUNTS.ORDER_ID, orderID)
                .set(APPLIEDDISCOUNTS.CODE, coupon)
                .execute();

        redisTemplate.delete("coupon-" + profileID);
    }

    public Response handleFreeCheckout(PaymentDTO paymentDTO) {
        try {
            handleReserveTicket(paymentDTO.getTickets(), paymentDTO.getProfileID());
        } catch (Exception e) {
            log.error("Error checking ticket availability: {}", e.getMessage());
            return new Response(HttpStatus.BAD_REQUEST.name(), e.getMessage(), null);
        }

        var orderID = context.insertInto(ORDERS)
                .set(ORDERS.USER_ID, paymentDTO.getUserID())
                .set(ORDERS.PROFILE_ID, paymentDTO.getProfileID())
                .set(ORDERS.EVENT_ID, UUID.fromString(paymentDTO.getEventID()))
                .set(ORDERS.STATUS, "paid")
                .returningResult(ORDERS.ORDER_ID)
                .fetchOneInto(Integer.class);

        try {
            ticketService.generateTickets(orderID, paymentDTO.getTickets(), paymentDTO.getEventID(), paymentDTO.getUserID(), paymentDTO.getProfileID(), false, null);
        } catch (Exception e) {
            log.error("Error generating tickets: {}", e.getMessage());
            return new Response(HttpStatus.INTERNAL_SERVER_ERROR.name(), e.getMessage(), null);
        }

        return new Response(HttpStatus.OK.name(), "Order successfully! Enjoy the event!!", null);
    }
}
