package com.nkd.accountservice.config;

import com.nkd.accountservice.service.JwtService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeignInterceptor implements RequestInterceptor {

    private final JwtService jwtService;

    @Autowired
    public FeignInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String token = jwtService.getInternalKey();
        requestTemplate.header("Authorization", "Bearer " + token);
    }
}
