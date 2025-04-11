package com.nkd.event.client;

import com.nkd.event.dto.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "gateway-service", path = "/accounts")
public interface AccountClient {

    @GetMapping("internal/user/id")
    Integer getUserID(@RequestParam("email") String email);

    @GetMapping("internal/profile/name")
    Map<Integer, String> getListProfileName(@RequestParam("id_list") String profileIdList);

    @GetMapping("internal/account/jwt")
    String getAccountJWTToken(@RequestParam("email") String email);

    @GetMapping("info")
    Map<String, Object> getAccountData(@RequestParam("pid") Integer profileID);

    @PostMapping("/event/attendee/info")
    List<Map<String, Object>> getEventAttendeeInfo(@RequestBody List<Integer> profileIDs);

    @GetMapping("/profile/email")
    String getProfileEmail(@RequestParam("pid") Integer profileID);

    @GetMapping("/user/suspend")
    Response suspendUser(@RequestParam("uid") String userID);
}
