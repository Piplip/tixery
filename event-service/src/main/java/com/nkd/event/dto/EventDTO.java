package com.nkd.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class EventDTO {

    private UUID eventID;
    private String[] images;
    private String[] videos;
    private String title;
    private String summary;
    private String eventType;
    private String eventDate;
    private String eventStartTime;
    private String eventEndTime;
    private OffsetDateTime eventStart;
    private OffsetDateTime eventEnd;
    private Boolean displayEndTime;
    private String timezone;
    private String language;
    private String locationType;
    private String location;
    private String longitude;
    private String latitude;
    private String locationName;
    private Boolean reserveSeating;
    private List<Faq> faqs;
    private List<TicketDTO> tickets;
    private String type;
    private String category;
    private String subCategory;
    private String[] tags;
    private String eventVisibility;
    private String visibilityStartDate;
    private String visibilityEndDate;
    private String visibilityStartTime;
    private String visibilityEndTime;
    private Boolean allowRefund;
    private Integer daysForRefund;
    private Boolean automatedRefund;
    private String publishType;
    private String publishDate;
    private String publishTime;
    private Integer capacity;
    private String status;
    private String additionalInfo;
    private List<Tier> tierData;
}

