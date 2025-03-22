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
public class Tier {

    private String dbTierID;
    private String tierID;
    private String name;
    private String perks;
    private String price;
    private Integer totalAssignedSeats;
    private List<String> assignedSeats;
    private String color;
    private String currency;
    private String currencySymbol;
    private String currencyFullForm;
}
