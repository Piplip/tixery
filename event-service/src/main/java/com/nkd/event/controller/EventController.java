package com.nkd.event.controller;

import com.nkd.event.dto.*;
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

    @PostMapping("/create/seatmap")
    public Response saveSeatMap(@RequestParam("eid") String eventID, @RequestBody SeatMapDTO data) {
        return eventService.saveSeatMap(eventID, data);
    }

    @PutMapping("/create/seatmap")
    public Response updateSeatMap(@RequestParam("mid") String mapID, @RequestBody SeatMapDTO data) {
        return eventService.updateSeatMap(mapID, data);
    }

    @GetMapping("/seat-map")
    public List<Map<String, Object>> getVenueSeatMap(@RequestParam("eid") String eventID, @RequestParam("pid") Integer profileID) {
        return eventService.getVenueSeatMap(eventID, profileID);
    }

    @GetMapping("/seat-map/data")
    public Response getVenueSeatMap(@RequestParam("mid") Integer mapID) {
        return eventService.getSeatMapInfo(mapID);
    }

    @GetMapping("/seat-map/tiers")
    public List<Map<String, Object>> getSeatMapTiers(@RequestParam("smid") Integer seatMapID) {
        return eventService.getSeatMapTiers(seatMapID);
    }

    @DeleteMapping("/seat-map/tier")
    public Response deleteTier(@RequestParam("mid") Integer seatMapID, @RequestParam("tid") Integer tierID) {
        return eventService.deleteTier(seatMapID, tierID);
    }

    @PostMapping("/update/recurrence")
    public Response updateRecurrenceEvent(@RequestParam("eid") String eventID, @RequestParam("tz") Integer timezone,
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
                                            @RequestParam(value = "is_organizer", required = false) Boolean isOrganizer,
                                            @RequestParam(value = "tz", required = false) Integer timezone) {
        return eventService.getEvent(eventID, profileID, isOrganizer, timezone);
    }
    
    @GetMapping("/get")
    public List<Map<String, Object>> getAllEvents(@RequestParam("uid") Integer userID,
                                                  @RequestParam(value = "past", required = false, defaultValue = "false") String getPast) {
        return eventService.getAllEvents(userID, getPast);
    }

    @GetMapping("/get/online")
    public Map<String, Object> getOnlineEventInfo(@RequestParam("eid") String eventID) {
        return eventService.getOnlineEventInfo(eventID);
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
    public List<Map<String, Object>> getSuggestedEvents(@RequestParam(value = "limit", defaultValue = "12") Integer limit,
                                                        @RequestParam(value = "pid", defaultValue = "") Integer profileID,
                                                        @RequestParam(value = "lat", required = false) String lat,
                                                        @RequestParam(value = "lon", required = false) String lon) {
        return eventService.getSuggestedEvents(limit, profileID, lat, lon);
    }

    @GetMapping("/events/cost")
    public List<Map<String, Object>> getSuggestedEventsByCost(@RequestParam("lat") String lat, @RequestParam("lon") String lon,
                                                              @RequestParam("val") Double cost) {
        return eventService.getSuggestedEventsByCost(lat, lon, cost);
    }

    @GetMapping("/events/online")
    public List<Map<String, Object>> getSuggestedOnlineEvents(@RequestParam("lat") String lat, @RequestParam("lon") String lon) {
        return eventService.getOnlineEvents(lat, lon);
    }

    @GetMapping("/events/time")
    public List<Map<String, Object>> getSuggestedEventByTime(@RequestParam("lat") String lat, @RequestParam("lon") String lon,
                                                             @RequestParam("val") String timeType) {
        return eventService.getSuggestedEventByTime(lat, lon, timeType);
    }

    @GetMapping("/events/bounds")
    public List<Map<String, Object>> getEventsByMapBounds(@RequestParam("northeast_lat") String northEastLat, @RequestParam("northeast_lon") String northEastLon,
                                                          @RequestParam("southwest_lat") String southWestLat, @RequestParam("southwest_lon") String southWestLon) {
        return eventService.getEventsByMapBounds(northEastLat, northEastLon, southWestLat, southWestLon);
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

    @PostMapping("/create/auto")
    public Response createEventWithAI(@RequestParam("pid") Integer profileID, @RequestParam("uid") Integer userID,
                                      @RequestParam(value = "price", required = false) Double price,
                                      @RequestParam(value = "free", defaultValue = "false") Boolean isFree, @RequestBody EventDTO eventDTO){
        return eventService.createEventWithAI(userID, profileID, price, isFree, eventDTO);
    }

    @GetMapping("/organizer/report")
    public Response getOrganizerReport(@RequestParam("uid") Integer userID, @RequestParam(value = "pid", required = false) Integer profileID,
                                       @RequestParam("start") String startDate, @RequestParam("end") String endDate) {
        return eventService.getOrganizerReport(userID, profileID, startDate, endDate);
    }

    @PostMapping("/event/report")
    public Response handleReportEvent(@RequestBody ReportDTO report, @RequestParam("tz") Integer timezone) {
        return eventService.handleReportEvent(report, timezone);

    }

    @GetMapping("/event/dashboard")
    public Map<String, Object> loadEventInfo(@RequestParam("eid") String eventID) {
        return eventService.loadEventInfo(eventID);
    }
}
