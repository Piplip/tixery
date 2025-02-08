package com.nkd.accountservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotifyPreference {

    @JsonProperty("feature_announcement")
    private Boolean featureAnnouncement;
    @JsonProperty("additional_info")
    private Boolean additionalInfo;
    @JsonProperty("organizer_announces")
    private Boolean organizerAnnounces;
    @JsonProperty("event_on_sales")
    private Boolean eventOnSales;
    @JsonProperty("liked_events")
    private Boolean likedEvents;
    @JsonProperty("event_sales_recap")
    private Boolean eventSalesRecap;
    @JsonProperty("important_reminders")
    private Boolean importantReminders;
    @JsonProperty("order_confirmations")
    private Boolean orderConfirmations;
    @JsonProperty("organizer_pay_update")
    private Boolean organizerPayUpdate;
    @JsonProperty("popular_events")
    private Boolean popularEvents;

    public static String defaultAttendeePreferences(){
        return """
                {
                    "feature_announcement": true,
                    "additional_info": true,
                    "organizer_announces": true,
                    "event_on_sales": false,
                    "liked_events": true,
                    "organizer_pay_update": false,
                    "popular_events": false
                }
                """;
    }

    public static String defaultOrganizerPreferences(){
        return """
                {
                    "feature_announcement": true,
                    "event_sales_recap": true,
                    "important_reminders": true,
                    "order_confirmations": true
                }
                """;
    }

    public void combinePreferences(NotifyPreference oldPreferences){
        if(oldPreferences == null){
            return;
        }
        new NotifyPreference(
            featureAnnouncement == null ? oldPreferences.featureAnnouncement : featureAnnouncement,
            additionalInfo == null ? oldPreferences.additionalInfo : additionalInfo,
            organizerAnnounces == null ? oldPreferences.organizerAnnounces : organizerAnnounces,
            eventOnSales == null ? oldPreferences.eventOnSales : eventOnSales,
            likedEvents == null ? oldPreferences.likedEvents : likedEvents,
            eventSalesRecap == null ? oldPreferences.eventSalesRecap : eventSalesRecap,
            importantReminders == null ? oldPreferences.importantReminders : importantReminders,
            orderConfirmations == null ? oldPreferences.orderConfirmations : orderConfirmations,
            organizerPayUpdate == null ? oldPreferences.organizerPayUpdate : organizerPayUpdate,
            popularEvents == null ? oldPreferences.popularEvents : popularEvents
        );
    }

    public String buildAttendeePreferences() {
        return """
            {
                "feature_announcement": %s,
                "additional_info": %s,
                "organizer_announces": %s,
                "event_on_sales": %s,
                "liked_events": %s,
                "organizer_pay_update": %s,
                "popular_events": %s
            }
            """.formatted(
                Boolean.TRUE.equals(featureAnnouncement),
                Boolean.TRUE.equals(additionalInfo),
                Boolean.TRUE.equals(organizerAnnounces),
                Boolean.TRUE.equals(eventOnSales),
                Boolean.TRUE.equals(likedEvents),
                Boolean.TRUE.equals(organizerPayUpdate),
                Boolean.TRUE.equals(popularEvents)
        );
    }

    public String buildOrganizerPreferences() {
        return """
            {
                "feature_announcement": %s,
                "event_sales_recap": %s,
                "important_reminders": %s,
                "order_confirmations": %s
            }
            """.formatted(
                Boolean.TRUE.equals(featureAnnouncement),
                Boolean.TRUE.equals(eventSalesRecap),
                Boolean.TRUE.equals(importantReminders),
                Boolean.TRUE.equals(orderConfirmations)
        );
    }
}
