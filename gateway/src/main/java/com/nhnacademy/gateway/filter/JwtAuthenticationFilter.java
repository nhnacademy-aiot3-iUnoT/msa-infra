package com.nhnacademy.gateway.filter;

import com.nhnacademy.gateway.global.config.JwtAuthProperties;
import com.nhnacademy.gateway.dto.ApiResponse;
import com.nhnacademy.gateway.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private final ReactiveJwtDecoder jwtDecoder;
    private final JsonMapper jsonMapper;
    private final JwtAuthProperties jwtAuthProperties;
    private final PathMatcher pathMatcher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String path = request.getPath().value();
        boolean shouldNotFilter = jwtAuthProperties.publicPaths().stream()
                .anyMatch(publicPath -> pathMatcher.match(publicPath, path));

        if (shouldNotFilter) {
            log.debug("public path 통과 - path={}", path);
            return chain.filter(exchange);
        }

        String token = getJwtToken(request);
        if (token == null) {
            log.debug("토큰 없음 - path={}", path);
            return unauthorized(exchange, ErrorCode.UNAUTHORIZED);
        }

        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    log.debug("토큰 인증 성공 - path={}", path);
                    return chain.filter(exchange);
                })
                .onErrorResume(JwtValidationException.class, e -> {
                        boolean isExpired = e.getErrors().stream()
                                .filter(ex -> ex.getDescription() != null)
                                .anyMatch(ex -> ex.getDescription().contains("expired"));

                        log.debug("토큰 검증 실패 - path={}, expired={}", path, isExpired);

                        return (isExpired)
                                ? unauthorized(exchange, ErrorCode.EXPIRED_TOKEN)
                                : unauthorized(exchange, ErrorCode.INVALID_TOKEN);
                })
                .onErrorResume(JwtException.class, e -> {
                    log.debug("토큰 파싱 실패 - path={}", path);
                    return unauthorized(exchange, ErrorCode.INVALID_TOKEN);
                });
    }

    @Override
    public int getOrder() {
        return 1;
    }

    private String getJwtToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        return authorization.substring(7);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> response = ApiResponse.error(errorCode);

        byte[] bytes = jsonMapper.writeValueAsBytes(response);

        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}