package cl.duoc.cloudnative.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean
    TopicExchange productEventsExchange(
            @Value("${app.messaging.product-events-exchange}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    TopicExchange productEventsDeadLetterExchange(
            @Value("${app.messaging.product-events-dlx}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    TopicExchange productAuditDeadLetterExchange(
            @Value("${app.messaging.product-audit-dlx}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    TopicExchange productMetricsDeadLetterExchange(
            @Value("${app.messaging.product-metrics-dlx}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue productEventsQueue(
            @Value("${app.messaging.product-events-queue}") String queueName,
            @Value("${app.messaging.product-events-dlx}") String deadLetterExchange,
            @Value("${app.messaging.product-events-dead-routing-key}") String deadRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    Queue productEventsDeadLetterQueue(
            @Value("${app.messaging.product-events-dlq}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Queue productAuditQueue(
            @Value("${app.messaging.product-audit-queue}") String queueName,
            @Value("${app.messaging.product-audit-dlx}") String deadLetterExchange,
            @Value("${app.messaging.product-audit-dead-routing-key}") String deadRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    Queue productAuditDeadLetterQueue(
            @Value("${app.messaging.product-audit-dlq}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Queue productMetricsQueue(
            @Value("${app.messaging.product-metrics-queue}") String queueName,
            @Value("${app.messaging.product-metrics-dlx}") String deadLetterExchange,
            @Value("${app.messaging.product-metrics-dead-routing-key}") String deadRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    Queue productMetricsDeadLetterQueue(
            @Value("${app.messaging.product-metrics-dlq}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Binding productEventsBinding(
            @Qualifier("productEventsQueue") Queue productEventsQueue,
            @Qualifier("productEventsExchange") TopicExchange productEventsExchange,
            @Value("${app.messaging.product-events-binding-key}") String bindingKey
    ) {
        return BindingBuilder.bind(productEventsQueue).to(productEventsExchange).with(bindingKey);
    }

    @Bean
    Binding productEventsDeadLetterBinding(
            @Qualifier("productEventsDeadLetterQueue") Queue productEventsDeadLetterQueue,
            @Qualifier("productEventsDeadLetterExchange") TopicExchange productEventsDeadLetterExchange,
            @Value("${app.messaging.product-events-dead-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(productEventsDeadLetterQueue)
                .to(productEventsDeadLetterExchange)
                .with(routingKey);
    }

    @Bean
    Binding productAuditBinding(
            @Qualifier("productAuditQueue") Queue productAuditQueue,
            @Qualifier("productEventsExchange") TopicExchange productEventsExchange,
            @Value("${app.messaging.product-audit-binding-key}") String bindingKey
    ) {
        return BindingBuilder.bind(productAuditQueue).to(productEventsExchange).with(bindingKey);
    }

    @Bean
    Binding productAuditDeadLetterBinding(
            @Qualifier("productAuditDeadLetterQueue") Queue productAuditDeadLetterQueue,
            @Qualifier("productAuditDeadLetterExchange") TopicExchange productAuditDeadLetterExchange,
            @Value("${app.messaging.product-audit-dead-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(productAuditDeadLetterQueue)
                .to(productAuditDeadLetterExchange)
                .with(routingKey);
    }

    @Bean
    Binding productMetricsBinding(
            @Qualifier("productMetricsQueue") Queue productMetricsQueue,
            @Qualifier("productEventsExchange") TopicExchange productEventsExchange,
            @Value("${app.messaging.product-metrics-binding-key}") String bindingKey
    ) {
        return BindingBuilder.bind(productMetricsQueue).to(productEventsExchange).with(bindingKey);
    }

    @Bean
    Binding productMetricsDeadLetterBinding(
            @Qualifier("productMetricsDeadLetterQueue") Queue productMetricsDeadLetterQueue,
            @Qualifier("productMetricsDeadLetterExchange") TopicExchange productMetricsDeadLetterExchange,
            @Value("${app.messaging.product-metrics-dead-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(productMetricsDeadLetterQueue)
                .to(productMetricsDeadLetterExchange)
                .with(routingKey);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of("productEvent", ProductEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
