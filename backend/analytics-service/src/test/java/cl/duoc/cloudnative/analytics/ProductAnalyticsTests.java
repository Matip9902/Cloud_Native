package cl.duoc.cloudnative.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAnalyticsTests {
    @Mock KafkaTemplate<String, Object> kafka;
    @Mock MetricRepository repository;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ProductAnalytics analytics;
    @BeforeEach void setup() { analytics = new ProductAnalytics(mapper, new ProductAnalyticsService(new KafkaMetricPublisher(kafka, "product.metrics"), repository)); }
    private ProductEvent event(String type) {
        return new ProductEvent(UUID.randomUUID(), type, 7L, "Monitor", OffsetDateTime.now());
    }
    @Test void transformsSupportedEventIntoTypedMetric() throws Exception {
        String type = "PRODUCT_CREATED";
        ProductEvent event = event(type);
        when(kafka.send(eq("product.metrics"), eq("7"), any(ProductMetric.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        analytics.process(mapper.writeValueAsString(event));
        ArgumentCaptor<ProductMetric> metric = ArgumentCaptor.forClass(ProductMetric.class);
        verify(kafka).send(eq("product.metrics"), eq("7"), metric.capture());
        assertThat(metric.getValue().eventId).isEqualTo(event.eventId());
        assertThat(metric.getValue().eventType).isEqualTo(type);
        assertThat(metric.getValue().productId).isEqualTo(7L);
        assertThat(metric.getValue().occurredAt.toInstant()).isEqualTo(event.occurredAt().toInstant());
        verifyNoInteractions(repository);
    }
    @Test void brokerFailurePropagatesSoListenerCanRetry() throws Exception {
        when(kafka.send(anyString(), anyString(), any(ProductMetric.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        String json = mapper.writeValueAsString(event("PRODUCT_CREATED"));
        assertThatThrownBy(() -> analytics.process(json)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }
    @Test void unsupportedProductEventDoesNotProduceMetric() throws Exception {
        String json = mapper.writeValueAsString(event("INVALID_TEST"));
        assertThatThrownBy(() -> analytics.process(json)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(kafka, repository);
    }
    @Test void redeliveryDoesNotSaveMetricTwice() throws Exception {
        ProductEvent event = event("PRODUCT_CREATED");
        when(repository.existsById(event.eventId())).thenReturn(false, true);
        String json = mapper.writeValueAsString(new ProductMetric(event));
        analytics.project(json); analytics.project(json);
        ArgumentCaptor<ProductMetric> saved = ArgumentCaptor.forClass(ProductMetric.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().eventId).isEqualTo(event.eventId());
        assertThat(saved.getValue().eventType).isEqualTo("PRODUCT_CREATED");
    }
}
