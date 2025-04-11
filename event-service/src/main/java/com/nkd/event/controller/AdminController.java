package com.nkd.event.controller;

import com.nkd.event.dto.EventReportDTO;
import com.nkd.event.dto.Response;
import com.nkd.event.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        return adminService.getOverviewMetrics();
    }

    @GetMapping("/reports")
    public Map<String, Object> getReports(@RequestParam("page") Integer page, @RequestParam("size") Integer pageSize) {
        return adminService.getEventReports(page, pageSize);
    }

    @PostMapping("/report")
    public Response updateReport(@RequestBody EventReportDTO eventReportDTO) {
        return adminService.updateEventReport(eventReportDTO);
    }
}
