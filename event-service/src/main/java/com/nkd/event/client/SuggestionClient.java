package com.nkd.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "suggestion-server", url = "http://localhost:5000")
public interface SuggestionClient {

    @GetMapping("/suggestions")
    List<String> getEventSuggestions(@RequestParam("profile_id") Integer profileID);
}
