package com.nkd.event.controller;

import com.nkd.event.dto.*;
import com.nkd.event.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets/add")
    public Response addTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone,
                              @RequestParam(value = "is_recurring", defaultValue = "false") Boolean isRecurring) {
        return ticketService.addTicket(eventID, ticketDTO, timezone, isRecurring);
    }

    @PostMapping("/tickets/tier/add")
    public Response addTierTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return ticketService.addTierTicket(eventID, ticketDTO, timezone);
    }

    @PostMapping("/tickets/tier/update")
    public Response updateTierTicket(@RequestParam(name = "eid") String eventID, @RequestBody TicketTier ticketTier, @RequestParam("timezone") Integer timezone) {
        return ticketService.updateTierTicket(eventID, ticketTier, timezone);
    }

    @PutMapping("/tickets/update")
    public Response updateTicket(@RequestParam(name = "tid") Integer ticketID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return ticketService.updateTicket(ticketID, ticketDTO, timezone);
    }

    @PostMapping("/tickets/remove")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID, @RequestParam(value = "is_recurring", defaultValue = "false") Boolean isRecurring) {
        return ticketService.deleteTicket(ticketID, isRecurring);
    }

    @GetMapping("/order/tickets")
    public List<Map<String, Object>> getOrderTicket(@RequestParam("order-id") Integer orderID) {
        return ticketService.getOrderTicket(orderID);
    }

    @PostMapping("/ticket/download")
    public ResponseEntity<?> downloadTicket(@RequestBody PrintTicketDTO printTicketDTO) {
        return ticketService.downloadTicket(printTicketDTO);
    }

    @PostMapping("/coupon/use")
    public Response handleCoupon(@RequestParam("coupon") String coupon, @RequestParam("pid") Integer profileID) {
        return ticketService.handleCoupon(coupon, profileID);
    }

    @PostMapping("/coupon/activate")
    public Response activateCoupon(@RequestParam("pid") Integer profileID, @RequestBody List<CouponDTO> coupon) {
        return ticketService.activateCoupon(profileID, coupon);
    }

    @GetMapping("/tier-tickets")
    public List<Map<String, Object>> getTierTicket(@RequestParam("eid") String eventID) {
        return ticketService.getTierTicket(eventID);
    }

}
