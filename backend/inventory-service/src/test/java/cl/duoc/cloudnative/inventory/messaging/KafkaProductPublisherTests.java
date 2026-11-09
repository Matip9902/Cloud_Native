package cl.duoc.cloudnative.inventory.messaging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaProductPublisherTests {
    private KafkaTemplate<String, Object> kafka;
    private KafkaProductPublisher publisher;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() {
        kafka = mock(KafkaTemplate.class);
        publisher = new KafkaProductPublisher(kafka, "product.events");
        logger = (Logger) LoggerFactory.getLogger(KafkaProductPublisher.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void cleanup() { logger.detachAppender(logs); logs.stop(); }
    private ProductEvent event(String type) {
        return new ProductEvent(UUID.randomUUID(), type, 15L, "Monitor", OffsetDateTime.now());
    }
    private void success(ProductEvent event) {
        var record = new ProducerRecord<String, Object>("product.events", "15", event);
        var metadata = new RecordMetadata(new TopicPartition("product.events", 2), 0, 8, 0, 2, 100);
        when(kafka.send("product.events", "15", event))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(record, metadata)));
    }

    @Test void logsAsynchronousFailureWithEventId() {
        ProductEvent event = event("PRODUCT_CREATED");
        var future = new CompletableFuture<SendResult<String, Object>>();
        when(kafka.send("product.events", "15", event)).thenReturn(future);
        publisher.publish(event);
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("Kafka publish failed")
                && e.getFormattedMessage().contains(event.eventId().toString()) && e.getThrowableProxy() != null);
    }
    @Test void sendsOnlyAfterTransactionCommits() {
        ProductEvent event = event("PRODUCT_CREATED"); success(event);
        try (var context = context()) {
            new TransactionTemplate(new TestTransactions()).executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(kafka);
            });
            verify(kafka).send("product.events", "15", event);
        }
    }
    @Test void doesNotPublishRolledBackChanges() {
        try (var context = context()) {
            new TransactionTemplate(new TestTransactions()).executeWithoutResult(status -> {
                context.publishEvent(event("PRODUCT_UPDATED")); status.setRollbackOnly();
            });
            verifyNoInteractions(kafka);
        }
    }
    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(KafkaProductPublisher.class, () -> publisher);
        context.refresh();
        return context;
    }
    // Spring transaction synchronization is real; this test double avoids a database dependency.
    static class TestTransactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
