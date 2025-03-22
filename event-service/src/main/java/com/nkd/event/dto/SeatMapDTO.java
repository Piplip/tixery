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
public class SeatMapDTO {

    private Integer ownerID;
    private String name;
    private String mapURL;
    private Boolean isPublic;
    private Integer capacity;
    private List<Tier> tiers;
}
