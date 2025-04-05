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

    public String buildAttendeePreferences() {
        return """
            {
                "feature_announcement": %s,
                "organizer_announces": %s,
                "event_on_sales": %s,
                "liked_events": %s
            }
            """.formatted(
                Boolean.TRUE.equals(featureAnnouncement),
                Boolean.TRUE.equals(organizerAnnounces),
                Boolean.TRUE.equals(eventOnSales),
                Boolean.TRUE.equals(likedEvents)
        );
    }

    public String buildOrganizerPreferences() {
        return """
            {
                "feature_announcement": %s,
                "event_sales_recap": %s,
                "important_reminders": %s
            }
            """.formatted(
                Boolean.TRUE.equals(featureAnnouncement),
                Boolean.TRUE.equals(eventSalesRecap),
                Boolean.TRUE.equals(importantReminders)
        );
    }
}
