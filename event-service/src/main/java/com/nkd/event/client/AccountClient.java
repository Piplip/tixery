package com.nkd.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gateway-service", path = "/accounts")
public interface AccountClient {

    @GetMapping("internal/user/id")
    Integer getUserID(@RequestParam("email") String email);
}
