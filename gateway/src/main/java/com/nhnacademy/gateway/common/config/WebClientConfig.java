package com.nhnacademy.gateway.common.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @Profile("prod")
    public WebClient webClient(@LoadBalanced WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @Profile("!prod")
    public WebClient devWebClient(WebClient.Builder builder) {
        return builder.build();
    }
}
