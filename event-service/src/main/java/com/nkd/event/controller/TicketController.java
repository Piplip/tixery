package com.nkd.event.controller;

import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
import com.nkd.event.service.EventService;
import com.nkd.event.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets/add")
    public Response addTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return ticketService.addTicket(eventID, ticketDTO, timezone);
    }

    @PutMapping("/tickets/update")
    public Response updateTicket(@RequestParam(name = "tid") Integer ticketID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return ticketService.updateTicket(ticketID, ticketDTO, timezone);
    }

    @PostMapping("/tickets/remove")
    public Response updateTicket(@RequestParam(name = "tid") Integer ticketID) {
        return ticketService.deleteTicket(ticketID);
    }
}
