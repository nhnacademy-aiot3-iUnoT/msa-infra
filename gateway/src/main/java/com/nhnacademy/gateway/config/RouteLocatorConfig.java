package com.nhnacademy.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("team1-account",
                        p -> p.path("/api/admin/accounts/**").uri("lb://account"))
                .route("team1-rule-engine",
                        p -> p.path("/api/rule-engine/**").uri("lb://rule-engine"))
                .build();
    }
}
