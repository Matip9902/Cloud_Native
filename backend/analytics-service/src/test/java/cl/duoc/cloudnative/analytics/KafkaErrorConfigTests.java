package cl.duoc.cloudnative.analytics;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.*;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class KafkaErrorConfigTests {
    private KafkaTemplate<String, Object> kafka;
    private Consumer<String, Object> consumer;
    private MessageListenerContainer container;
    private DefaultErrorHandler handler;
    private final ConsumerRecord<String, Object> record = new ConsumerRecord<>("product.events", 1, 9, "42", "invalid-payload");
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        kafka = mock(KafkaTemplate.class); consumer = mock(Consumer.class); container = mock(MessageListenerContainer.class);
        handler = new KafkaErrorConfig().kafkaErrorHandler(kafka);
    }
    @AfterEach void cleanup() { handler.clearThreadState(); }
    private void partitionExists() {
        when(consumer.partitionsFor(eq("product.events.DLT"), any(Duration.class))).thenReturn(List.of(
                new PartitionInfo("product.events.DLT", 1, Node.noNode(), new Node[0], new Node[0])));
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void retriesTwiceThenPublishesOriginalRecordToSamePartitionOfDlt() {
        partitionExists();
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        Exception failure = new IllegalStateException("invalid business event");
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        verify(kafka, never()).send(any(ProducerRecord.class));
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
        ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("product.events.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(1);
        assertThat(sent.getValue().key()).isEqualTo("42");
        assertThat(sent.getValue().value()).isEqualTo("invalid-payload");
        assertThat(sent.getValue().headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void failedDltPublicationDoesNotMarkRecordRecovered() {
        partitionExists();
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("DLT unavailable")));
        Exception failure = new IllegalStateException("processing failed");
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        verify(kafka).send(any(ProducerRecord.class));
    }
}
