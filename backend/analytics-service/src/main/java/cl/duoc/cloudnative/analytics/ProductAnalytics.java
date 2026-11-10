package cl.duoc.cloudnative.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ProductAnalytics {
    private final ObjectMapper mapper;
    private final ProductAnalyticsService analytics;

    public ProductAnalytics(ObjectMapper mapper, ProductAnalyticsService analytics) {
        this.mapper = mapper;
        this.analytics = analytics;
    }

    @KafkaListener(topics = "${app.kafka.product-topic}", groupId = "product-analytics-v1",
            concurrency = "${app.kafka.product-concurrency:1}")
    public void process(String payload) throws Exception {
        analytics.process(mapper.readValue(payload, ProductEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.metrics-topic}", groupId = "metrics-projection-v1", concurrency = "1")
    public void project(String payload) throws Exception {
        analytics.project(mapper.readValue(payload, ProductMetric.class));
    }
}
