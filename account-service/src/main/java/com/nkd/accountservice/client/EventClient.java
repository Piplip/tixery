package com.nkd.accountservice.client;

import com.nkd.accountservice.config.FeignInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gateway-service", path = "/events", configuration = FeignInterceptor.class)
public interface EventClient {

    @GetMapping("/event/favorite/total")
    Integer getTotalFavouriteEvent(@RequestParam("pid") Integer profileID);
}
