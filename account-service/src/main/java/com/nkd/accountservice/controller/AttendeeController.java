package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.NotifyPreference;
import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.jooq.types.UInteger;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AttendeeController {

    private final AccountService accountService;

    @PostMapping("/follow")
    public Response handleFollowOrganizer(@RequestParam("upid") Integer profileID, @RequestParam("opid") Integer organizerID, @RequestParam("follow") Boolean follow){
        return accountService.handleFollowOrganizer(profileID, organizerID, follow);
    }

    @GetMapping("/follow")
    public List<Integer> getFollow(@RequestParam("pid") Integer profileID){
        return accountService.getFollow(profileID);
    }

    @PostMapping("/follow/detail")
    public List<Map<String, Object>> getFollowDetail(@RequestBody List<UInteger> profileIDs){
        return accountService.getFollowDetail(profileIDs);
    }

    @GetMapping("/attendee/stats")
    public Map<String, Object> getAttendeeStats(@RequestParam("pid") String profileID){
        return accountService.getAttendeeStats(profileID);
    }

    @GetMapping("/attendee/profiles")
    public String getOrganizerProfiles(@RequestParam(value = "u") String email){
        return accountService.getProfiles(email);
    }

    @PostMapping("/notification/preferences/update")
    public Response updateNotificationPreferences(@RequestParam("pid") Integer profileID, @RequestParam("role") String role, @RequestBody NotifyPreference preferences){
        return accountService.updateNotificationPreferences(profileID, role, preferences);
    }

    @GetMapping("/notification/preferences")
    public String getNotificationPreferences(@RequestParam("pid") Integer profileID){
        return accountService.getNotificationPreferences(profileID);
    }

    @GetMapping("/attendee/profile")
    public Map<String, Object> getAttendeeProfile(@RequestParam("pid") String profileID){
        return accountService.getAttendeeProfile(profileID);
    }

    @PutMapping("/attendee/profile/update")
    public Response updateAttendeeProfile(@RequestParam("pid") Integer profileID, @RequestParam("udid") Integer userDataID, @RequestBody Profile profile){
        return accountService.updateAttendeeProfile(profileID, userDataID, profile);
    }

    @GetMapping("/attendee/interest")
    public String getInterest(@RequestParam("udid") Integer userDataID){
        return accountService.getInterest(userDataID);
    }

    @PostMapping("/attendee/interest")
    public Response handleInterest(@RequestParam("udid") Integer userDataID, @RequestBody String interests){
        return accountService.handleSaveInterest(userDataID, interests);
    }

    @GetMapping("/order/attendee/info")
    public Map<String, Object> getOrderAttendeeInfo(@RequestParam("pid") Integer profileID){
        return accountService.getOrderAttendeeInfo(profileID);
    }
}
