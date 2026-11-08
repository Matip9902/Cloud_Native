package cl.duoc.cloudnative.audit.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean
    org.springframework.amqp.rabbit.connection.ConnectionNameStrategy connectionNameStrategy() {
        return factory -> "audit-service";
    }

    @Bean
    TopicExchange productAuditExchange(
            @Value("${app.messaging.product-events-exchange}") String exchangeName
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
    Binding productAuditBinding(
            Queue productAuditQueue,
            TopicExchange productAuditExchange,
            @Value("${app.messaging.product-audit-binding-key}") String bindingKey
    ) {
        return BindingBuilder.bind(productAuditQueue).to(productAuditExchange).with(bindingKey);
    }

    @Bean
    Binding productAuditDeadLetterBinding(
            Queue productAuditDeadLetterQueue,
            TopicExchange productAuditDeadLetterExchange,
            @Value("${app.messaging.product-audit-dead-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(productAuditDeadLetterQueue)
                .to(productAuditDeadLetterExchange)
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
