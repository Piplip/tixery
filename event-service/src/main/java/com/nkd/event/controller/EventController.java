package com.nkd.event.controller;

import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class EventController {

    private final EventService eventService;

    @Autowired
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/create/request")
    public Response createEventRequest(@RequestParam String pid, @RequestParam(name = "u") String email) {
        return eventService.createEventRequest(pid, email);
    }

    @PostMapping("/create")
    public Response createEvent(@RequestParam("step") String step, @RequestParam(value = "eid") String eventID, @RequestBody EventDTO eventDTO) {
        return eventService.createEvent(eventDTO, eventID, step);
    }

    @PostMapping("delete")
    public Response deleteEvent(@RequestParam("eid") String eventID) {
        return eventService.deleteEvent(eventID);
    }

    @GetMapping("/get/specific")
    public Map<String, Object> getEventById(@RequestParam("eid") String eventID) {
        return eventService.getEvent(eventID);
    }
    
    @GetMapping("/get")
    public List<Map<String, Object>> getAllEvents(@RequestParam("uid") Integer userID) {
        return eventService.getAllEvents(userID);
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
    public List<Map<String, Object>> getSuggestedEvents(@RequestParam("limit") Integer limit, @RequestParam("oid") Integer organizerID) {
        return eventService.getSuggestedEvents(limit, organizerID);
    }
    
    @PostMapping("/tickets/add")
    public Response addTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return eventService.addTicket(eventID, ticketDTO, timezone);
    }

    @PutMapping("/tickets/update")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return eventService.updateTicket(ticketID, ticketDTO, timezone);
    }

    @PostMapping("/tickets/remove")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID) {
        return eventService.deleteTicket(ticketID);
    }
}
