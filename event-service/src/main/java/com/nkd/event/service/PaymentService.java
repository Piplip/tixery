package com.nkd.event.service;

import com.nkd.event.dto.PaymentDTO;
import com.nkd.event.dto.StripeResponse;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.enumeration.PaymentStatus;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.nkd.event.Tables.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${stripe.secret-key}")
    private String secretKey;
    @Value("${client.port}")
    private String port;

    private final DSLContext context;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, List<TicketDTO>> cache = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher publisher;

    // TODO: Handle the capped ticket quantity
    @Transactional
    public StripeResponse handleStripeCheckout(PaymentDTO paymentDTO) {
        Stripe.apiKey = secretKey;

        var orderID = context.insertInto(ORDERS)
                .set(ORDERS.USER_ID, paymentDTO.getUserID())
                .set(ORDERS.PROFILE_ID, paymentDTO.getProfileID())
                .set(ORDERS.EVENT_ID, UUID.fromString(paymentDTO.getEventID()))
                .set(ORDERS.STATUS, "pending")
                .returningResult(ORDERS.ORDER_ID)
                .fetchOneInto(Integer.class);

        redisTemplate.opsForValue().set("stripe-order-" + orderID, Map.of("eventID", paymentDTO.getEventID(),
                    "userID", paymentDTO.getUserID(), "profileID", paymentDTO.getProfileID(), "amount", paymentDTO.getAmount(),
                    "currency", paymentDTO.getCurrency(), "email", paymentDTO.getEmail(), "username", paymentDTO.getUsername()), 10, TimeUnit.MINUTES);
        cache.put("stripe-order-" + orderID, paymentDTO.getTickets());

        SessionCreateParams params = createStripeParams(paymentDTO, orderID);
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

    // TODO: Handle create Attendee record when close to event date
    @Transactional
    public StripeResponse handleSuccessfulStripePayment(Integer orderID) {
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

                List<TicketDTO> tickets = cache.get("stripe-order-" + orderID);
                generateTickets(orderID, tickets, outerMap.get("eventID").toString(), ((Integer) outerMap.get("userID")),
                        ((Integer) outerMap.get("profileID")));

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
            } catch (Exception e) {
                response.setStatus("failed");
                response.setMessage("Internal server error");
                log.error("Error getting tickets: {}", e.getMessage());
            }
        }

        return response;
    }

    public StripeResponse handleFailedStripePayment(Integer orderID, PaymentStatus status) {
        if(orderID == null) {
            return StripeResponse.builder().status("failed").message("Order ID is required").build();
        }

        if(!context.fetchExists(ORDERS, ORDERS.ORDER_ID.eq(orderID))) {
            return StripeResponse.builder().status("failed").message("Order does not exist").build();
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

    @Transactional
    protected void generateTickets(Integer orderID, List<TicketDTO> tickets, String eventID, Integer userID, Integer profileID) {
        tickets.forEach(ticket -> {
            var orderItemID = context.insertInto(ORDERITEMS)
                    .set(ORDERITEMS.ORDER_ID, orderID)
                    .set(ORDERITEMS.TICKET_TYPE_ID, ticket.getTicketTypeID())
                    .set(ORDERITEMS.QUANTITY, ticket.getQuantity())
                    .set(ORDERITEMS.PRICE, new BigDecimal(ticket.getPrice()))
                    .returningResult(ORDERITEMS.ORDER_ITEM_ID)
                    .fetchOneInto(Integer.class);
            context.insertInto(TICKETS)
                    .set(TICKETS.EVENT_ID, UUID.fromString(eventID))
                    .set(TICKETS.ORDER_ITEM_ID, orderItemID)
                    .set(TICKETS.USER_ID, userID)
                    .set(TICKETS.PROFILE_ID, profileID)
                    .set(TICKETS.PURCHASE_DATE, OffsetDateTime.now())
                    .set(TICKETS.STATUS, "active")
                    .execute();
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

    private SessionCreateParams createStripeParams(PaymentDTO paymentDTO, Integer orderID) {
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
        
        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:" + port + "/payment/success" + "?orderID=" + orderID)
                .setCancelUrl("http://localhost:" + port + "/payment/error" + "?orderID=" + orderID)
                .addLineItem(lineItem)
                .build();
    }

}
