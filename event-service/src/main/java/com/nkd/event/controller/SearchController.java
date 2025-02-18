package com.nkd.event.controller;

import com.nkd.event.dto.Response;
import com.nkd.event.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public List<Map<String, Object>> getEventsSearch(@RequestParam(value = "q", required = false) String query,
                                                     @RequestParam(value = "category", required = false) String categories,
                                                     @RequestParam(value = "sub_category", required = false) String subCategory,
                                                     @RequestParam(value = "lat", required = false) String lat,
                                                     @RequestParam(value = "lon", required = false) String lon,
                                                     @RequestParam(value = "date", required = false) String time,
                                                     @RequestParam(value = "price", required = false) String price,
                                                     @RequestParam(value = "online", required = false) Boolean online,
                                                     @RequestParam(value = "followed", required = false) Boolean isFollowOnly,
                                                     @RequestBody(required = false) List<Integer> followList) {
        return searchService.getEventSearch(query, categories, subCategory, lat, lon, time, price, online, isFollowOnly, followList);
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

    @GetMapping("/search/orders")
    public List<Map<String, Object>> loadOrders(@RequestParam(value = "q", defaultValue = "") String query,
                                                @RequestParam(value = "range", defaultValue = "3") Integer range,
                                                @RequestParam("pid") Integer organizerID) {
        return searchService.loadOrders(query, range, organizerID);
    }
}
