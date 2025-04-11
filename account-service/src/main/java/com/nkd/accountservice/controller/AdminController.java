package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.domain.UserDataDTO;
import com.nkd.accountservice.service.impl.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public Map<String, Object> loadUsers(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                               @RequestParam(value = "size", defaultValue = "50") Integer size) {
        return adminService.loadUsers(page, size);
    }

    @GetMapping("/user")
    public Map<String, Object> loadUsers(@RequestParam("role") String role, @RequestParam("uid") String userID) {
        return adminService.loadUserDetail(role, userID);
    }

    @PutMapping("/user")
    public Response updateUser(@RequestBody UserDataDTO userDataDTO) {
        return adminService.updateUserData(userDataDTO);
    }

    @DeleteMapping("/user")
    public Response deleteUser(@RequestParam("uid") String userID) {
        return adminService.deleteUser(userID);
    }

    @GetMapping("/analytics")
    public Map<String, Object> getAnalytics(
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return adminService.getAnalytics(propertyId, startDate, endDate);
    }

    @GetMapping("/overview")
    public Map<String, Object> getOverviewMetrics() {
        return adminService.getOverviewMetrics();
    }

    @GetMapping("/user/suspend")
    public Response suspendUser(@RequestParam("uid") String userID) {
        return adminService.suspendUser(userID);
    }

}
