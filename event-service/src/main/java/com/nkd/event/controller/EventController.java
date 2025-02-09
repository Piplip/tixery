package com.nkd.event.controller;

import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.OnlineEventDTO;
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

    @PostMapping("delete")
    public Response deleteEvent(@RequestParam("eid") String eventID) {
        return eventService.deleteEvent(eventID);
    }

    @GetMapping("/get/specific")
    public Map<String, Object> getEventById(@RequestParam("eid") String eventID, @RequestParam(value = "pid", required = false) Integer profileID) {
        return eventService.getEvent(eventID, profileID);
    }
    
    @GetMapping("/get")
    public List<Map<String, Object>> getAllEvents(@RequestParam("uid") Integer userID,
                                                  @RequestParam(value = "past", required = false, defaultValue = "false") String getPast) {
        return eventService.getAllEvents(userID, getPast);
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

    @GetMapping("/search")
    public List<Map<String, Object>> getEventsSearch(@RequestParam("eids") String eventIDs) {
        return eventService.getEventSearch(eventIDs);
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
        return eventService.getEventOrders(profileID, lastID);
    }
    @PostMapping("/order/cancel")
    public Response cancelOrder(@RequestParam("order-id") Integer orderID, @RequestParam("uname") String username, @RequestParam("u") String email) {
        return paymentService.cancelOrder(orderID, username, email);
    }
}
