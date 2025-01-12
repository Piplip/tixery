package com.nkd.event.controller;

import com.nkd.event.dto.EventDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    
    @PostMapping("/tickets/add")
    public Response addTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return eventService.addTicket(eventID, ticketDTO, timezone);
    }

    @PutMapping("/tickets/update")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return eventService.updateTicket(ticketID, ticketDTO, timezone);
    }

    // TODO: Figure out why can't call to this endpoint
    @PostMapping("/tickets/remove")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID) {
        return eventService.deleteTicket(ticketID);
    }
}
