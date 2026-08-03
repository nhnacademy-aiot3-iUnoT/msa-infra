package com.nhnacademy.gateway.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RouteLocatorConfig {
    private final ServiceUrlProperties urlProperties;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("team1-account", p ->
                        p.path("/api/auth/**", "/api/accounts/**").uri(urlProperties.account()))
                .route("team1-rule-engine", p ->
                        p.path("/api/rule-engine/**").uri(urlProperties.ruleEngine()))
                .route("team1-inventory", p ->
                        p.path("/api/admin/medicines/**", "/api/core/**").uri(urlProperties.inventory()))
                .build();
    }
}
