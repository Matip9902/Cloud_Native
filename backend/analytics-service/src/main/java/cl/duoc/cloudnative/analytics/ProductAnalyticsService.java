package cl.duoc.cloudnative.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Application logic shared by the Kafka adapters. Exceptions must reach the retry/DLT handler. */
@Service
public class ProductAnalyticsService {
    private static final Set<String> EVENT_TYPES = Set.of(
            "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED");

    private final MetricPublisher publisher;
    private final MetricRepository repository;

    public ProductAnalyticsService(MetricPublisher publisher, MetricRepository repository) {
        this.publisher = publisher;
        this.repository = repository;
    }

    public void process(ProductEvent event) throws Exception {
        if (event == null || event.eventId() == null || event.productId() == null || event.occurredAt() == null
                || !EVENT_TYPES.contains(event.eventType() == null ? "" : event.eventType())) {
            throw new IllegalArgumentException("Invalid product event");
        }
        // Return only after the broker acknowledges, before committing the input offset.
        publisher.publish(new ProductMetric(event));
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
