package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.impl.AdminService;
import lombok.RequiredArgsConstructor;
import org.jooq.types.UInteger;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @DeleteMapping("/user")
    public Response deleteUser(@RequestParam("uid") String userID) {
        return adminService.deleteUser(userID);
    }
}
