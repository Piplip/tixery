package com.nkd.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@EnableWebFlux
public class GatewayConfig {

    @Bean
    public RouterFunction<ServerResponse> fallbackRoutes() {
        return route(GET("/fallback"), req ->
                ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Mono.just("Service is currently unavailable. Please try later."), String.class));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange ->
                Mono.just(Objects
                        .requireNonNull(
                                exchange.getRequest()
                                        .getRemoteAddress()).
                        getAddress().
                        getHostAddress());
    }
}
