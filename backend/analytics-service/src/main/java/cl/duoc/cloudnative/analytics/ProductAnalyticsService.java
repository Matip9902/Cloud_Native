package cl.duoc.cloudnative.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Application logic shared by the Kafka adapters. Exceptions must reach the retry/DLT handler. */
@Service
public class ProductAnalyticsService {
    private static final Set<String> EVENT_TYPES = Set.of(
            "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED");

    private final KafkaTemplate<String, Object> kafka;
    private final MetricRepository repository;
    private final String metricsTopic;

    public ProductAnalyticsService(KafkaTemplate<String, Object> kafka, MetricRepository repository,
            @Value("${app.kafka.metrics-topic}") String metricsTopic) {
        this.kafka = kafka;
        this.repository = repository;
        this.metricsTopic = metricsTopic;
    }

    public void process(ProductEvent event) throws Exception {
        if (event == null || event.eventId() == null || event.productId() == null || event.occurredAt() == null
                || !EVENT_TYPES.contains(event.eventType() == null ? "" : event.eventType())) {
            throw new IllegalArgumentException("Invalid product event");
        }
        // Return only after the broker acknowledges, before committing the input offset.
        kafka.send(metricsTopic, event.productId().toString(), new ProductMetric(event))
                .get(30, TimeUnit.SECONDS);
    }

    @Transactional
    public void project(ProductMetric metric) {
        if (metric == null || metric.eventId == null || metric.productId == null || metric.occurredAt == null
                || !EVENT_TYPES.contains(metric.eventType == null ? "" : metric.eventType)) {
            throw new IllegalArgumentException("Invalid metric event");
        }
        // Replaying an event must not increment the projection twice.
        if (!repository.existsById(metric.eventId)) {
            repository.save(metric);
        }
    }
}
