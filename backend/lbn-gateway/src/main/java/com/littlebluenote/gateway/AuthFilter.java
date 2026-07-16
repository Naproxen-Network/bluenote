package com.littlebluenote.gateway;

import com.littlebluenote.common.Constants;
import com.littlebluenote.common.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway-level JWT authentication. Verifies the bearer token, and forwards the
 * resolved user id / role to downstream services as trusted headers.
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    // endpoints that do not require authentication
    private static final List<String> WHITELIST = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/admin/login",
            "/api/user/avatar/", "/actuator", "/avatars", "/ws/chat"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing token");
        }
        String token = auth.substring(7);
        try {
            Claims claims = JwtUtil.parse(token);
            String uid = claims.getSubject();
            String role = String.valueOf(claims.get("role"));
            // Never forward caller-supplied identity headers. Only values derived from
            // the verified JWT are trusted by downstream services.
            ServerHttpRequest mutated = request.mutate().headers(headers -> {
                headers.remove(Constants.HEADER_USER_ID);
                headers.remove(Constants.HEADER_USER_ROLE);
                headers.set(Constants.HEADER_USER_ID, uid);
                headers.set(Constants.HEADER_USER_ROLE, role);
            }).build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange, "invalid token");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"message\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
