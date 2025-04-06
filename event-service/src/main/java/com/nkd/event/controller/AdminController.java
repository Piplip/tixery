package com.nkd.event.controller;

import com.nkd.event.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/event-stats")
    public Map<String, Object> getEventStats(@RequestParam("start_date") String startDate, @RequestParam("end_date") String endDate) {
        return adminService.getEventStats(startDate, endDate);
    }

    @GetMapping("/events")
    public Map<String, Object> getEvents(@RequestParam("start_date") String startDate, @RequestParam("end_date") String endDate,
                                               @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        return adminService.getEvents(startDate, endDate, page, size);
    }
}
