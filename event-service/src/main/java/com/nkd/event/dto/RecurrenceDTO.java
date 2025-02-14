package com.nkd.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RecurrenceDTO {

    private Integer occurrenceID;
    private String startDate;
    private String startTime;
    private String endTime;
    private List<TicketDTO> tickets;
}
