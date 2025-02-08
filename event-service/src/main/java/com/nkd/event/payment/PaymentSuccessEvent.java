package com.nkd.event.payment;

import com.nkd.event.dto.TicketDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PaymentSuccessEvent {

    private List<TicketDTO> tickets;
    private String eventID;
    private String userID;
    private String email;
    private String profileID;
    private Long amount;
    private String currency;

}
