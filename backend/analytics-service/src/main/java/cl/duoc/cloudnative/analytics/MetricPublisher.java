package cl.duoc.cloudnative.analytics;

/** Output port: success means the metric has been acknowledged by its transport. */
public interface MetricPublisher {
    void publish(ProductMetric metric) throws Exception;
}
