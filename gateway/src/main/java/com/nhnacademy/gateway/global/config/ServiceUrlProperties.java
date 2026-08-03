package com.nhnacademy.gateway.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.url")
public record ServiceUrlProperties(
        String account,
        String ruleEngine,
        String inventory
) {}