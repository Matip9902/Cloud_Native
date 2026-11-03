package cl.duoc.cloudnative.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ProductAnalytics {
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, Object> kafka;
    private final MetricRepository repository;
    private final String metricsTopic;
    public ProductAnalytics(ObjectMapper mapper, KafkaTemplate<String, Object> kafka, MetricRepository repository,
            @Value("${app.kafka.metrics-topic}") String metricsTopic) {
        this.mapper=mapper; this.kafka=kafka; this.repository=repository; this.metricsTopic=metricsTopic;
    }
    @KafkaListener(topics="${app.kafka.product-topic}", groupId="product-analytics-v1")
    public void process(String payload) throws Exception {
        ProductEvent event=mapper.readValue(payload, ProductEvent.class);
        if (event.eventId()==null || event.productId()==null || event.occurredAt()==null ||
                !Set.of("PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED").contains(event.eventType()==null ? "" : event.eventType()))
            throw new IllegalArgumentException("Invalid product event");
        // Forward one typed metric per operation. Commit the input offset only after Kafka acknowledges.
        kafka.send(metricsTopic, event.productId().toString(), new ProductMetric(event)).get(30, TimeUnit.SECONDS);
    }
    @Transactional
    @KafkaListener(topics="${app.kafka.metrics-topic}", groupId="metrics-projection-v1", concurrency="1")
    public void project(String payload) throws Exception {
        ProductMetric metric=mapper.readValue(payload, ProductMetric.class);
        if (metric.eventId==null || metric.productId==null || metric.occurredAt==null ||
                !Set.of("PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED").contains(metric.eventType==null ? "" : metric.eventType))
            throw new IllegalArgumentException("Invalid metric event");
        // A replay or retry must not increment the same operation twice.
        if (!repository.existsById(metric.eventId)) repository.save(metric);
    }
}
