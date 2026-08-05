package com.nhnacademy.gateway.filter;

import com.nhnacademy.gateway.global.config.JwtAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private ReactiveJwtDecoder jwtDecoder;

    private GatewayFilterChain filterChain;
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private AtomicBoolean filterChainCalled;

    private final String publicPath = "/api/accounts";
    private final String wildcardPublicPath = "/api/accounts/pwd/**";
    private final String privatePath = "/api/rule-engine";

    @BeforeEach
    void setUp() {
        JwtAuthProperties properties = new JwtAuthProperties(List.of(publicPath, wildcardPublicPath));
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtDecoder, JsonMapper.builder().build(), properties, new AntPathMatcher());
        filterChainCalled = new AtomicBoolean(false);

        filterChain = exchange -> {
            filterChainCalled.set(true);
            return Mono.empty();
        };
    }

    @Test
    @DisplayName("public-path로 요청하면 토큰 검증을 하지 않는다.")
    void filter_WhenRequestPublicPath_ShouldNotTokenVerify() {
        // given
        ServerWebExchange exchange = requestWithToken(publicPath, "");

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        then(jwtDecoder)
                .should(never())
                .decode(anyString());

        assertThat(filterChainCalled)
                .isTrue();

        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("와일드카드로 등록된 public-path로 요청하면 토큰 검증을 하지 않는다.")
    void filter_WhenRequestWildcardPublicPath_ShouldNotTokenVerify() {
        // given
        ServerWebExchange exchange = requestWithToken("/api/accounts/pwd/reset/test", "");

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        then(jwtDecoder)
                .should(never())
                .decode(anyString());

        assertThat(filterChainCalled)
                .isTrue();

        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("와일드카드가 아닌 public-path로 등록된 하위 경로는 토큰을 검증한다.")
    void filter_WhenRequestPrivatePath_ShouldTokenVerify() {
        // given
        String token = "token";
        ServerWebExchange exchange = requestWithToken("/api/accounts/1", token);

        given(jwtDecoder.decode(token))
                .willReturn(Mono.empty());

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        then(jwtDecoder)
                .should()
                .decode(token);
    }

    @Test
    @DisplayName("정상적인 토큰으로 요청하면 401 상태 코드가 반환되지 않는다.")
    void filter_WhenValidToken_DoesNotReturnsUnauthorized() {
        // given
        String validToken = "valid-token";
        ServerWebExchange exchange = requestWithToken(privatePath, validToken);

        Jwt jwt = Jwt.withTokenValue(validToken)
                .header("alg", "RS256")
                .claim("sub", UUID.randomUUID())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        given(jwtDecoder.decode(validToken))
                .willReturn(Mono.just(jwt));

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        then(jwtDecoder)
                .should()
                .decode(validToken);

        assertThat(filterChainCalled)
                .isTrue();

        // 성공 시 필터가 상태 코드를 건드리지 않음
        assertThat(exchange.getResponse().getStatusCode())
                .isNull();
    }

    @Test
    @DisplayName("토큰이 존재하지 않으면 401 상태코드를 반환한다.")
    void filter_WhenTokenMissing_ReturnsUnauthorized() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get(privatePath)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 토큰이면 401 상태 코드를 반환한다.")
    void filter_WhenTokenExpired_ReturnsUnauthorized() {
        // given
        String expiredToken = "expired-token";
        OAuth2Error expiredError = new OAuth2Error("invalid_token", "Jwt expired at ...", null);

        given(jwtDecoder.decode(expiredToken))
                .willReturn(Mono.error(new JwtValidationException("expired", List.of(expiredError))));

        ServerWebExchange exchange = requestWithToken(privatePath, expiredToken);

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 401 상태 코드를 반환한다.")
    void filter_WhenInvalidToken_ReturnsUnauthorized() {
        // given
        String invalidToken = "invalid-token";
        OAuth2Error invalidError = new OAuth2Error("invalid_token", "Signed JWT rejected ...", null);

        given(jwtDecoder.decode(invalidToken))
                .willReturn(Mono.error(new JwtValidationException("invalid", List.of(invalidError))));

        ServerWebExchange exchange = requestWithToken(privatePath, invalidToken);

        // when & then
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ServerWebExchange requestWithToken(String path, String token) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header("Authorization", "Bearer " + token)
                .build();

        return MockServerWebExchange.from(request);
    }
}