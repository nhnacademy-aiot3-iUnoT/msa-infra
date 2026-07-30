package com.nhnacademy.gateway.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    @Value("${services.account.url}")
    private String accountUrl;

    @Value("${services.rule-engine.url}")
    private String ruleEngineUrl;

    @Value("${services.inventory.url}")
    private String inventoryUrl;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("team1-account", p ->
                        p.path("/api/auth/**", "/api/accounts/**").uri(accountUrl))
                .route("team1-rule-engine", p ->
                        p.path("/api/rule-engine/**").uri(ruleEngineUrl))
                .route("team1-inventory", p ->
                        p.path("/api/admin/medicines/**", "/api/core/**").uri(inventoryUrl))
                .build();
    }
}
