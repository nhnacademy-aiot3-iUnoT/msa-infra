package com.nhnacademy.gateway.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtAuthProperties(
        List<String> publicPaths
) {}
