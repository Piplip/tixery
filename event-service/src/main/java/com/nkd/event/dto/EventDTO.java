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
public class EventDTO {

    private String[] images;
    private String[] videos;
    private String title;
    private String summary;
    private String eventType;
    private String eventDate;
    private String eventStartTime;
    private String eventEndTime;
    private Boolean displayEndTime;
    private String timezone;
    private String language;
    private String locationType;
    private String location;
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
}

