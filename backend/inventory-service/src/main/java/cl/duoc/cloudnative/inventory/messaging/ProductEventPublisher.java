package cl.duoc.cloudnative.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final String routingKeyPrefix;

    public ProductEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.product-events-exchange}") String exchangeName,
            @Value("${app.messaging.product-events-routing-key-prefix}") String routingKeyPrefix
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.routingKeyPrefix = routingKeyPrefix;
    }

    public void publish(ProductEvent event) {
        String routingKey = routingKeyFor(event.eventType());
        rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
        log.info("Evento de producto publicado: exchange={}, routingKey={}, type={}, productId={}, eventId={}",
                exchangeName, routingKey, event.eventType(), event.productId(), event.eventId());
    }

    private String routingKeyFor(String eventType) {
        String action = eventType.toLowerCase().replace("product_", "");
        return routingKeyPrefix + "." + action;
    }
}
