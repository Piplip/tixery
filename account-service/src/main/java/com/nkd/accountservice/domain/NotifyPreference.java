package com.nkd.accountservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotifyPreference {

    private Boolean featureAnnouncement;
    private Boolean additionalInfo;
    private Boolean organizerAnnounces;
    private Boolean eventOnSales;
    private Boolean likedEvents;
    private Boolean eventSalesRecap;
    private Boolean importantReminders;
    private Boolean orderConfirmations;

}
