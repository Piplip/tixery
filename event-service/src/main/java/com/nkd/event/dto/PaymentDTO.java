package com.nkd.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class PaymentDTO {

    private String email;
    private Integer userID;
    private Integer profileID;
    private String eventID;
    private List<TicketDTO> tickets;
    private Long amount;
    private Long quantity;
    private String name;
    private String currency;
    private String username;
}
