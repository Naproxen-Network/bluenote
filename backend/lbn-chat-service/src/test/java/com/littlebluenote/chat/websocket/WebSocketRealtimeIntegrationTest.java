package com.littlebluenote.chat.websocket;

import com.littlebluenote.chat.config.WebSocketConfig;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

import java.lang.reflect.Type;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = WebSocketRealtimeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "chat.websocket.allowed-origin-patterns=http://localhost:*"
        })
class WebSocketRealtimeIntegrationTest {
    @LocalServerPort
    private int port;

    private WebSocketStompClient client;

    @AfterEach
    void stopClient() {
        if (client != null) client.stop();
    }

    @Test
    void authenticatesAndRoundTripsAFrameOnThePrivateUserQueue() throws Exception {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + JwtUtil.issue(77L, Constants.ROLE_USER));
        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws/chat",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<Map<?, ?>> received = new CompletableFuture<>();
        session.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((Map<?, ?>) payload);
            }
        });
        session.send("/app/chat/typing", Map.of("conversationId", 9L, "typing", true));

        Map<?, ?> payload = received.get(5, TimeUnit.SECONDS);
        assertEquals("77", String.valueOf(payload.get("userId")));
        assertEquals("9", String.valueOf(payload.get("conversationId")));
        session.disconnect();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RabbitAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, EchoTypingController.class})
    static class TestApplication {}

    @Controller
    static class EchoTypingController {
        @MessageMapping("/chat/typing")
        @SendToUser("/queue/typing")
        public Map<String, Object> echo(Map<String, Object> body, Principal principal) {
            return Map.of(
                    "conversationId", body.get("conversationId"),
                    "typing", body.get("typing"),
                    "userId", principal.getName());
        }
    }
}
