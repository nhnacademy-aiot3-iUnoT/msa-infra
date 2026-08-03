package com.nhnacademy.gateway.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
class WebClientConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("prod 프로필에서는 LB WebClient가 등록된다.")
    void webClient_WhenProdProfile_RegistersLBWebClient() {
        assertThat(context.containsBean("webClient"))
                .isTrue();

        assertThat(context.containsBean("devWebClient"))
                .isFalse();
    }
}