package com.nkd.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReportDTO {

    private String eventID;
    private Integer reporterProfileID;
    private String reporterEmail;
    private String detail;
    private String reason;
}
