package cl.duoc.cloudnative.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
    @BeforeEach void setup() { analytics = new ProductAnalytics(mapper, kafka, repository, "product.metrics"); }
    private ProductEvent event(String type) {
        return new ProductEvent(UUID.randomUUID(), type, 7L, "Monitor", OffsetDateTime.now());
    }
    private Map<String, Object> payload() {
        return new LinkedHashMap<>(Map.of("eventId", UUID.randomUUID().toString(), "eventType", "PRODUCT_CREATED",
                "productId", 7L, "productName", "Monitor", "occurredAt", OffsetDateTime.now().toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED"})
    void transformsSupportedEventIntoTypedMetric(String type) throws Exception {
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
    @Test void waitsForBrokerAcknowledgementBeforeReturning() throws Exception {
        @SuppressWarnings("unchecked") CompletableFuture<SendResult<String, Object>> send = mock(CompletableFuture.class);
        when(kafka.send(anyString(), anyString(), any(ProductMetric.class))).thenReturn(send);
        analytics.process(mapper.writeValueAsString(event("PRODUCT_CREATED")));
        verify(send).get(30, TimeUnit.SECONDS);
    }
    @Test void brokerFailurePropagatesSoListenerCanRetry() throws Exception {
        when(kafka.send(anyString(), anyString(), any(ProductMetric.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        String json = mapper.writeValueAsString(event("PRODUCT_CREATED"));
        assertThatThrownBy(() -> analytics.process(json)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }
    @Test void malformedJsonDoesNotReachKafka() {
        assertThatThrownBy(() -> analytics.process("{bad-json")).isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(kafka, repository);
    }
    @Test void unsupportedProductEventDoesNotProduceMetric() throws Exception {
        String json = mapper.writeValueAsString(event("INVALID_TEST"));
        assertThatThrownBy(() -> analytics.process(json)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(kafka, repository);
    }
    @ParameterizedTest @ValueSource(strings = {"eventId", "eventType", "productId", "occurredAt"})
    void incompleteEventDoesNotProduceMetric(String field) throws Exception {
        Map<String, Object> payload = payload(); payload.remove(field);
        String json = mapper.writeValueAsString(payload);
        assertThatThrownBy(() -> analytics.process(json)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(kafka, repository);
    }
    @Test void savesNewMetricAsIndependentProjection() throws Exception {
        ProductEvent event = event("PRODUCT_UPDATED");
        analytics.project(mapper.writeValueAsString(new ProductMetric(event)));
        ArgumentCaptor<ProductMetric> saved = ArgumentCaptor.forClass(ProductMetric.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().eventId).isEqualTo(event.eventId());
        assertThat(saved.getValue().eventType).isEqualTo("PRODUCT_UPDATED");
        verifyNoInteractions(kafka);
    }
    @Test void redeliveryDoesNotSaveMetricTwice() throws Exception {
        ProductEvent event = event("PRODUCT_CREATED");
        when(repository.existsById(event.eventId())).thenReturn(false, true);
        String json = mapper.writeValueAsString(new ProductMetric(event));
        analytics.project(json); analytics.project(json);
        verify(repository, times(1)).save(any(ProductMetric.class));
    }
    @ParameterizedTest @ValueSource(strings = {"eventId", "eventType", "productId", "occurredAt"})
    void incompleteMetricNeverTouchesDatabase(String field) throws Exception {
        Map<String, Object> payload = payload(); payload.remove("productName"); payload.remove(field);
        String json = mapper.writeValueAsString(payload);
        assertThatThrownBy(() -> analytics.project(json)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, kafka);
    }
    @Test void unknownMetricTypeNeverTouchesDatabase() throws Exception {
        String json = mapper.writeValueAsString(new ProductMetric(event("INVALID_TEST")));
        assertThatThrownBy(() -> analytics.project(json)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, kafka);
    }
    @Test void databaseFailurePropagatesSoMetricCanBeRetried() throws Exception {
        when(repository.save(any(ProductMetric.class))).thenThrow(new IllegalStateException("database down"));
        String json = mapper.writeValueAsString(new ProductMetric(event("PRODUCT_CREATED")));
        assertThatThrownBy(() -> analytics.project(json)).isInstanceOf(IllegalStateException.class);
    }
    @Test void summaryCountsOperationsWithoutConfusingThemWithCurrentStock() {
        when(repository.findAll()).thenReturn(List.of(new ProductMetric(event("PRODUCT_CREATED")),
                new ProductMetric(event("PRODUCT_CREATED")), new ProductMetric(event("PRODUCT_DELETED"))));
        var result = new MetricsController(repository).summary();
        assertThat(result.get("total")).isEqualTo(3);
        assertThat(result.get("operations")).isEqualTo(Map.of("PRODUCT_CREATED", 2L, "PRODUCT_UPDATED", 0L, "PRODUCT_DELETED", 1L));
    }
    @Test void emptyProjectionReturnsZerosForAllSupportedOperations() {
        when(repository.findAll()).thenReturn(List.of());
        var result = new MetricsController(repository).summary();
        assertThat(result.get("total")).isEqualTo(0);
        assertThat(result.get("operations")).isEqualTo(Map.of("PRODUCT_CREATED", 0L, "PRODUCT_UPDATED", 0L, "PRODUCT_DELETED", 0L));
    }
}
