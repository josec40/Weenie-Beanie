package com.shbean.auth;

import java.util.Map;
import java.util.UUID;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Authenticates GraphQL-over-WebSocket sessions from the {@code connection_init} payload
 * ({@code {"Authorization": "Bearer <jwt>"}}) and exposes the user id to subscriptions as a context value.
 * Only subscriptions are served over the socket; queries and mutations stay on authenticated HTTP.
 */
@Component
public class WebSocketAuthInterceptor implements WebSocketGraphQlInterceptor {

    public static final String USER_ID = "userId";

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo session, Map<String, Object> payload) {
        Object header = payload.getOrDefault("Authorization", payload.get("authorization"));
        if (!(header instanceof String value) || !value.startsWith("Bearer ")) {
            return Mono.error(new IllegalStateException("Unauthorized"));
        }
        try {
            UUID userId = UUID.fromString(jwtDecoder.decode(value.substring("Bearer ".length())).getSubject());
            session.getAttributes().put(USER_ID, userId);
            return Mono.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Mono.error(new IllegalStateException("Unauthorized"));
        }
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, WebGraphQlInterceptor.Chain chain) {
        if (request instanceof WebSocketGraphQlRequest socket) {
            Object userId = socket.getSessionInfo().getAttributes().get(USER_ID);
            if (userId == null || !request.getDocument().stripLeading().startsWith("subscription")) {
                return Mono.error(new IllegalStateException("Only authenticated subscriptions are served over WebSocket"));
            }
            request.configureExecutionInput((input, builder) -> builder.graphQLContext(Map.of(USER_ID, userId)).build());
        }
        return chain.next(request);
    }
}
