package com.nkd.event.controller;

import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.OnlineEventDTO;
import com.nkd.event.dto.RecurrenceDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.service.EventService;
import com.nkd.event.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EventController {

    private final EventService eventService;
    private final PaymentService paymentService;

    @Autowired
    public EventController(EventService eventService, PaymentService paymentService) {
        this.eventService = eventService;
        this.paymentService = paymentService;
    }

    @PostMapping("/create/request")
    public Response createEventRequest(@RequestParam String pid, @RequestParam(name = "u") String email) {
        return eventService.createEventRequest(pid, email);
    }

    @PostMapping("/create")
    public Response createEvent(@RequestParam("step") String step, @RequestParam(value = "eid") String eventID, @RequestBody EventDTO eventDTO) {
        return eventService.createEvent(eventDTO, eventID, step);
    }

    @PostMapping("/create/online")
    public Response saveOnlineEvent(@RequestParam("eid") String eventID, @RequestBody OnlineEventDTO data) {
        return eventService.saveOnlineEventInfo(eventID, data);
    }

    @PostMapping("/update/recurrence")
    public Response updateRecurrenceEvent(@RequestParam("eid") String eventID, @RequestParam("timezone") Integer timezone,
                                        @RequestBody List<RecurrenceDTO> data) {
        return eventService.updateRecurrenceEvent(eventID, timezone, data);
    }

    @PostMapping("/delete/recurrence")
    public Response deleteOccurrence(@RequestParam("date") String date, @RequestParam("eid") String eventID) {
        return eventService.deleteOccurrence(date, eventID);
    }

    @PostMapping("delete")
    public Response deleteEvent(@RequestParam("eid") String eventID) {
        return eventService.deleteEvent(eventID);
    }

    @GetMapping("/get/specific")
    public Map<String, Object> getEventById(@RequestParam("eid") String eventID, @RequestParam(value = "pid", required = false) Integer profileID,
                                            @RequestParam(value = "is_organizer", required = false) Boolean isOrganizer) {
        return eventService.getEvent(eventID, profileID, isOrganizer);
    }
    
    @GetMapping("/get")
    public List<Map<String, Object>> getAllEvents(@RequestParam("uid") Integer userID, @RequestParam("tz") Integer timezone,
                                                  @RequestParam(value = "past", required = false, defaultValue = "false") String getPast) {
        return eventService.getAllEvents(userID, timezone,getPast);
    }

    @GetMapping("/get/related")
    public List<Map<String, Object>> getProfileRelatedEvents(@RequestParam("eid") String eventID) {
        return eventService.getProfileRelatedEvent(eventID);
    }

    @GetMapping("/get/profile")
    public List<Map<String, Object>> getAllProfileEvent(@RequestParam("pid") Integer profileID) {
        return eventService.getAllProfileEvent(profileID);
    }

    @GetMapping("/get/suggested")
    public List<Map<String, Object>> getSuggestedEvents(@RequestParam("limit") Integer limit) {
        return eventService.getSuggestedEvents(limit);
    }

    @GetMapping("/events/cost")
    public List<Map<String, Object>> getSuggestedEventsByCost(@RequestParam("lat") String lat, @RequestParam("lon") String lon,
                                                              @RequestParam("val") Double cost) {
        return eventService.getSuggestedEventsByCost(lat, lon, cost);
    }

    @GetMapping("/events/online")
    public List<Map<String, Object>> getSuugestedOnlineEvents(@RequestParam("lat") String lat, @RequestParam("lon") String lon) {
        return eventService.getOnlineEvents(lat, lon);
    }

    @GetMapping("/events/time")
    public List<Map<String, Object>> getSuggestedEventByTime(@RequestParam("lat") String lat, @RequestParam("lon") String lon,
                                                             @RequestParam("val") String timeType) {
        return eventService.getSuggestedEventByTime(lat, lon, timeType);
    }

    @GetMapping("/events/type")
    public List<Map<String, Object>> getSuggestedEventByType(@RequestParam("lat") String lat, @RequestParam("lon") String lon,
                                                             @RequestParam("val") String eventType) {
        return eventService.getSuggestedEventByType(lat, lon, eventType);
    }

    @GetMapping("/event/trends")
    public List<Map<String, Object>> getLocalizePopularEvents(@RequestParam("lat") String lat, @RequestParam("lon") String lon) {
        return eventService.getLocalizePopularEvents(lat, lon);
    }

    @PostMapping("/event/favorite/add")
    public Response addToFavourite(@RequestParam("eid") String eventID, @RequestParam("pid") Integer profileID) {
        return eventService.addToFavourite(eventID, profileID);
    }

    @PostMapping("/event/favorite/delete")
    public Response removeFromFavorite(@RequestParam("eid") String eventID, @RequestParam("pid") Integer profileID) {
        return eventService.removeFromFavorite(eventID, profileID);
    }

    @GetMapping("/event/favorite")
    public List<UUID> getFavouriteEventIDs(@RequestParam("pid") Integer profileID) {
        return eventService.getFavouriteEventIDs(profileID);
    }

    @PostMapping("/event/favorite/get")
    public List<Map<String, Object>> getFavouriteEvents(@RequestBody List<String> eventIDs) {
        return eventService.getFavouriteEvents(eventIDs);
    }

    @GetMapping("/event/favorite/total")
    public Integer getTotalFavouriteEvent(@RequestParam("pid") Integer profileID) {
        return eventService.getTotalFavouriteEvent(profileID);
    }

    @PostMapping("/event/followed")
    public List<Map<String, Object>> getFollowedEvents(@RequestBody List<Integer> organizerIDs) {
        return eventService.getFollowedEvents(organizerIDs);
    }

    @GetMapping("/orders/profile")
    public List<Map<String, Object>> getProfileOrders(@RequestParam("pid") Integer profileID
            , @RequestParam(name = "last-id", required = false) Integer lastID) {
        return eventService.getProfileOrders(profileID, lastID, false);
    }

    @GetMapping("/orders/profile/past")
    public List<Map<String, Object>> getPastProfileOrders(@RequestParam("pid") Integer profileID
            , @RequestParam(name = "last-id", required = false) Integer lastID) {
        return eventService.getProfileOrders(profileID, lastID, true);
    }

    @PostMapping("/order/cancel")
    public Response cancelOrder(@RequestParam("order-id") Integer orderID, @RequestParam("uname") String username, @RequestParam("u") String email) {
        return paymentService.cancelOrder(orderID, username, email);
    }
}
