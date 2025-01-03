package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrganizerDataController {

    private final UserDataService userDataService;

    @GetMapping("/organizer/profile")
    public String getOrganizerProfiles(@RequestParam(value = "u") String email){
        return userDataService.getOrganizerProfiles(email);
    }

    @PostMapping("/organizer/profile/create")
    public Response saveProfile(@RequestParam("u") String email, @RequestBody Profile profile){
        return userDataService.saveOrganizerProfile(email, profile);
    }

    @GetMapping("/organizer/profile/get")
    public Map<String, Object> getProfile(@RequestParam("pid") String profileId){
        return userDataService.getProfile(profileId);
    }

    @PostMapping("/organizer/profile/update")
    public Response updateProfile(@RequestParam("pid") String profileId, @RequestParam("u") String email, @RequestBody Profile profile){
        return userDataService.updateProfile(profileId, email, profile);
    }

    @DeleteMapping("/organizer/profile/delete")
    public Response deleteProfile(@RequestParam("pid") String profileId, @RequestParam("u") String email){
        return userDataService.deleteProfile(profileId, email);
    }

    @GetMapping("/organizer/profile/custom-url/check")
    public Response checkUniqueCustomURL(@RequestParam("url") String customURL){
        return userDataService.checkUniqueCustomURL(customURL);
    }
}
