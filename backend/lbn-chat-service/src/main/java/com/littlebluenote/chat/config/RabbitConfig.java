package com.littlebluenote.chat.config;

import com.littlebluenote.common.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class RabbitConfig {
    @Bean("chatInstanceId")
    public String chatInstanceId() {
        return UUID.randomUUID().toString();
    }

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(Constants.EXCHANGE_CHAT, true, false);
    }

    @Bean
    public TopicExchange chatDeadExchange() {
        return new TopicExchange(Constants.EXCHANGE_CHAT_DEAD, true, false);
    }

    @Bean
    public Queue chatWebsocketQueue(
            @org.springframework.beans.factory.annotation.Qualifier("chatInstanceId") String instanceId) {
        // Every service instance receives each event. A shared queue could route an
        // event to an instance whose in-memory STOMP broker does not own the session.
        return QueueBuilder.nonDurable(Constants.QUEUE_CHAT_WEBSOCKET + "." + instanceId)
                .exclusive()
                .autoDelete()
                .deadLetterExchange(Constants.EXCHANGE_CHAT_DEAD)
                .deadLetterRoutingKey("chat.dead")
                .build();
    }

    @Bean
    public Queue chatDeadQueue() {
        return QueueBuilder.durable(Constants.QUEUE_CHAT_DEAD).build();
    }

    @Bean
    public Binding chatWebsocketBinding(Queue chatWebsocketQueue) {
        return BindingBuilder.bind(chatWebsocketQueue).to(chatExchange()).with(Constants.RK_CHAT_EVENT);
    }

    @Bean
    public Binding chatDeadBinding() {
        return BindingBuilder.bind(chatDeadQueue()).to(chatDeadExchange()).with("chat.dead");
    }

    @Bean
    public MessageConverter chatJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate chatRabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }
}
