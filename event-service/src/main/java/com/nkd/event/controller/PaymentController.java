package com.nkd.event.controller;

import com.nkd.event.dto.PaymentDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.StripeResponse;
import com.nkd.event.enumeration.PaymentStatus;
import com.nkd.event.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/stripe/checkout")
    public ResponseEntity<StripeResponse> stripeCheckout(@RequestParam(value = "reserve", defaultValue = "false") Boolean isReserve, @RequestBody PaymentDTO paymentDTO) {
        StripeResponse response = paymentService.handleStripeCheckout(isReserve, paymentDTO);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/google-pay/checkout")
    public Response googlePayCheckout(@RequestParam(value = "reserve", defaultValue = "false") Boolean isReserve, @RequestBody PaymentDTO paymentDTO) {
        return paymentService.handleGooglePayCheckout(isReserve, paymentDTO);
    }

    @PostMapping("/stripe/success")
    public ResponseEntity<StripeResponse> handleSuccessfulStripePayment(@RequestParam("order-id") Integer orderID, @RequestParam("pid") Integer profileID,
                                                                        @RequestParam(value = "reserve", defaultValue = "false") Boolean isReserve) {
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.handleSuccessfulStripePayment(orderID, profileID, isReserve));
    }

    @PostMapping("/stripe/failure")
    public ResponseEntity<StripeResponse> handleFailedStripePayment(@RequestParam("order-id") Integer orderID, @RequestParam("pid") Integer profileID) {
        var response = paymentService.handleFailedStripePayment(orderID, profileID, PaymentStatus.USER_CANCELLED);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/free/checkout")
    public ResponseEntity<?> handleFreeCheckout(@RequestBody PaymentDTO paymentDTO) {
        Response response = paymentService.handleFreeCheckout(paymentDTO);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}
