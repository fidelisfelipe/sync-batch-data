package com.fidelis.syncbatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for sync job triggering.
 *
 * <p>Flow: producer → {@code sync.trigger.exchange} → {@code sync.trigger.queue}
 *         dead-letters → {@code sync.dead-letter.queue}
 */
@Configuration
public class RabbitMQConfig {

    public static final String SYNC_QUEUE          = "sync.trigger.queue";
    public static final String SYNC_EXCHANGE        = "sync.trigger.exchange";
    public static final String SYNC_ROUTING_KEY     = "sync.trigger";
    public static final String DEAD_LETTER_QUEUE    = "sync.dead-letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "sync.dead-letter.exchange";

    // ── Main queue (with dead-letter config) ─────────────────────────────────

    @Bean
    public Queue syncQueue() {
        return QueueBuilder.durable(SYNC_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "sync.dead")
                .withArgument("x-message-ttl", 300_000) // 5 min TTL
                .build();
    }

    @Bean
    public DirectExchange syncExchange() {
        return new DirectExchange(SYNC_EXCHANGE, true, false);
    }

    @Bean
    public Binding syncBinding() {
        return BindingBuilder.bind(syncQueue()).to(syncExchange()).with(SYNC_ROUTING_KEY);
    }

    // ── Dead-letter queue ─────────────────────────────────────────────────────

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("sync.dead");
    }

    // ── Message converter (JSON) ───────────────────────────────────────────────

    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter());
        factory.setDefaultRequeueRejected(false); // send failures to dead-letter
        return factory;
    }
}
