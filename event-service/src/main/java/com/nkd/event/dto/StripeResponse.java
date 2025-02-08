package com.nkd.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class StripeResponse {

    private String status;
    private String message;
    private String eventID;
    private String sessionID;
    private String sessionURL;
    private Long amount;
    private String currency;
    private String paymentMethod = "Stripe";
}
