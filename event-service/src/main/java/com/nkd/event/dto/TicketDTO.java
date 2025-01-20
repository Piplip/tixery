package com.nkd.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketDTO {

    private String ticketType;
    private String ticketName;
    private Integer quantity;
    private String price;
    private String startDate;
    private String startTime;
    private String endDate;
    private String endTime;
    private String description;
    private String visibility;
    private String visibleEndDate;
    private String visibleEndTime;
    private String visibleStartDate;
    private String visibleStartTime;
    private Integer minPerOrder;
    private Integer maxPerOrder;
    private Boolean absorbFee;
    private String currency;
    private String currencySymbol;
    private String currencyFullForm;
}
