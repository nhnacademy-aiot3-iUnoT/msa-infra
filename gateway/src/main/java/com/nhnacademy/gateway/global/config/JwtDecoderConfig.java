package com.nhnacademy.gateway.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class JwtDecoderConfig {
    private final WebClient webClient;
    private final ServiceUrlProperties urlProperties;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(urlProperties.account() + "/.well-known/jwks.json")
                .webClient(webClient)
                .build();
    }
}
