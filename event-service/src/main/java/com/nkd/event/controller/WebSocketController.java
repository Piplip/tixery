package com.nkd.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate simpMessagingTemplate;

    @PostMapping("/seat-map/state/update")
    public void updateMapState(@RequestParam("mid") String mapID, @RequestBody List<String> seatIDs) {
        simpMessagingTemplate.convertAndSend("/seat-map/" + mapID, seatIDs);
    }
}
