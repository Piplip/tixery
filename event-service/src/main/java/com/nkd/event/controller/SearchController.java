package com.nkd.event.controller;

import com.nkd.event.dto.Response;
import com.nkd.event.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search/suggestions")
    public List<Map<String, Object>> getEventSearchSuggestions(@RequestParam("q") String query, @RequestParam(name = "type", required = false) Integer type
            , @RequestParam(name = "lat", required = false) String lat, @RequestParam(name = "lon", required = false) String lon
            , @RequestParam(value = "uid", required = false) Integer userID) {
        return searchService.getEventSearchSuggestions(query, type, lat, lon, userID);
    }

    @GetMapping("/search/trends")
    public List<Map<String, Object>> getLocalizeSearchTrends(@RequestParam("lat") String lat, @RequestParam("lon") String lon) {
        return searchService.getLocalizeSearchTrends(lat, lon);
    }

    @GetMapping("/search/history")
    public List<Map<String, Object>> getSearchHistory(@RequestParam("uid") Integer userID) {
        return searchService.getSearchHistory(userID);
    }

    @PostMapping("/search/history/delete")
    public Response deleteSearchHistory(@RequestParam("search-id") Integer searchID) {
        return searchService.deleteSearchHistory(searchID);
    }
}
