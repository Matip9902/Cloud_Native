package cl.duoc.cloudnative.analytics;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;
@Configuration
public class KafkaErrorConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,Object> template) {
        DeadLetterPublishingRecoverer recoverer=new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic()+".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler handler=new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.setRetryListeners((record, exception, attempt) -> LoggerFactory.getLogger(KafkaErrorConfig.class)
                .warn("Kafka processing failed: topic={}, partition={}, offset={}, attempt={}",
                        record.topic(), record.partition(), record.offset(), attempt, exception));
        return handler;
    }
}
