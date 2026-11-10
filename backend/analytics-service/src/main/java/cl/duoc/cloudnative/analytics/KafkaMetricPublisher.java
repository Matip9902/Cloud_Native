package cl.duoc.cloudnative.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaMetricPublisher implements MetricPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public KafkaMetricPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${app.kafka.metrics-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @Override
    public void publish(ProductMetric metric) throws Exception {
        // Do not let the input listener commit before the broker acknowledges this output.
        kafka.send(topic, metric.productId.toString(), metric).get(30, TimeUnit.SECONDS);
    }
}
