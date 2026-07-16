package com.littlebluenote.recommend.config;

import com.littlebluenote.common.Constants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---- post events (from post-service) ----
    @Bean
    public TopicExchange postExchange() {
        return new TopicExchange(Constants.EXCHANGE_POST, true, false);
    }

    @Bean
    public Queue postQueue() {
        return new Queue(Constants.QUEUE_POST_EVENT, true);
    }

    @Bean
    public Binding postBinding() {
        return BindingBuilder.bind(postQueue()).to(postExchange()).with("post.#");
    }

    // ---- cross-layer committee events (from the Node.js layer-sync service) ----
    @Bean
    public TopicExchange layerExchange() {
        return new TopicExchange(Constants.EXCHANGE_LAYER, true, false);
    }

    @Bean
    public Queue layerQueue() {
        return new Queue(Constants.QUEUE_LAYER_EVENT, true);
    }

    @Bean
    public Binding layerBinding() {
        return BindingBuilder.bind(layerQueue()).to(layerExchange()).with("committee.#");
    }
}
