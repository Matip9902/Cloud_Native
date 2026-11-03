package cl.duoc.cloudnative.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KafkaProductPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaProductPublisher.class);
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;
    public KafkaProductPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${app.kafka.product-topic:${KAFKA_PRODUCT_TOPIC:product.events}}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }
    @TransactionalEventListener
    public void publish(ProductEvent event) {
        // Only publish committed product changes; RabbitMQ retains its existing flow.
        try {
            kafka.send(topic, event.productId().toString(), event).whenComplete((result, error) -> {
                if (error != null) log.error("Kafka publish failed: topic={}, eventId={}", topic, event.eventId(), error);
                else log.info("Kafka product published: eventId={}, partition={}, offset={}", event.eventId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            });
        } catch (Exception error) {
            log.error("Kafka publish failed after database commit: eventId={}", event.eventId(), error);
        }
    }
}
