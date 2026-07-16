package com.littlebluenote.chat.config;

import com.littlebluenote.chat.websocket.UserPrincipal;
import com.littlebluenote.common.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(@Value("${chat.websocket.allowed-origin-patterns:http://127.0.0.1:*,http://localhost:*}")
                           String[] allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Bean
    public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(chatHeartbeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Mutate the accessor attached to the original message. Creating a
                // wrapped copy would lose the Principal after the CONNECT frame.
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                StompCommand command = accessor.getCommand();
                if (StompCommand.CONNECT.equals(command)) authenticate(accessor);
                if (StompCommand.SUBSCRIBE.equals(command)) authorizeSubscribe(accessor);
                if (StompCommand.SEND.equals(command)) authorizeSend(accessor);
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        String header = values == null || values.isEmpty() ? null : values.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing WebSocket bearer token");
        }
        try {
            Claims claims = JwtUtil.parse(header.substring(7));
            long userId = Long.parseLong(claims.getSubject());
            accessor.setUser(new UserPrincipal(userId, String.valueOf(claims.get("role"))));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WebSocket bearer token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) throw new IllegalArgumentException("Unauthenticated subscription");
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/user/queue/")) {
            throw new IllegalArgumentException("Only private user queues may be subscribed");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) throw new IllegalArgumentException("Unauthenticated message");
        String destination = accessor.getDestination();
        if (!"/app/chat/typing".equals(destination)) {
            throw new IllegalArgumentException("Unsupported WebSocket command");
        }
    }
}
