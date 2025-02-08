package com.nkd.event.controller;

import com.nkd.event.dto.PaymentDTO;
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
    public ResponseEntity<StripeResponse> checkout(@RequestBody PaymentDTO paymentDTO) {
        StripeResponse response = paymentService.handleStripeCheckout(paymentDTO);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/stripe/success")
    public ResponseEntity<StripeResponse> handleSuccessfulStripePayment(@RequestParam("order-id") Integer orderID) {
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.handleSuccessfulStripePayment(orderID));
    }

    @PostMapping("/stripe/failure")
    public ResponseEntity<StripeResponse> handleFailedStripePayment(@RequestParam("order-id") Integer orderID) {
        var response = paymentService.handleFailedStripePayment(orderID, PaymentStatus.USER_CANCELLED);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}
