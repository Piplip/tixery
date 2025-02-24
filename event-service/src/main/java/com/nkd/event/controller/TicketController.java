package com.nkd.event.controller;

import com.nkd.event.dto.CouponDTO;
import com.nkd.event.dto.PrintTicketDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.dto.TicketDTO;
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

    @PutMapping("/tickets/update")
    public Response updateTicket(@RequestParam(name = "tid") Integer ticketID, @RequestBody TicketDTO ticketDTO, @RequestParam("timezone") Integer timezone) {
        return ticketService.updateTicket(ticketID, ticketDTO, timezone);
    }

    @PostMapping("/tickets/remove")
    public Response deleteTicket(@RequestParam(name = "tid") Integer ticketID, @RequestParam(value = "is_recurring", required = false) Boolean isRecurring) {
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
    public Response activateCoupon(@RequestBody List<CouponDTO> coupon) {
        return ticketService.activateCoupon(coupon);
    }

}
