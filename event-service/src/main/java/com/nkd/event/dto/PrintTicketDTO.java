package com.nkd.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintTicketDTO {

    private String eventImg;
    private String eventName;
    private String eventDate;
    private String orderDate;
    private String location;
    private String ticketName;
    private String quantity;
    private String currency;
    private float price;
    private Integer orderID;
    private Integer ticketID;
    private String eventID;
}
