package cl.duoc.cloudnative.kafkaadmin;
import java.util.Map;
import java.util.concurrent.*;
import org.apache.kafka.common.errors.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class KafkaApiErrors {
    @ExceptionHandler({ExecutionException.class, java.util.concurrent.TimeoutException.class})
    ResponseEntity<Map<String,String>> handle(Exception exception) {
        Throwable cause=exception instanceof ExecutionException ? exception.getCause() : exception;
        HttpStatus status=cause instanceof TopicExistsException ? HttpStatus.CONFLICT :
                cause instanceof UnknownTopicOrPartitionException || cause instanceof GroupIdNotFoundException ? HttpStatus.NOT_FOUND :
                cause instanceof InvalidPartitionsException || cause instanceof InvalidReplicationFactorException ||
                cause instanceof InvalidConfigurationException ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of("message",cause.getMessage()==null ? "Kafka unavailable" : cause.getMessage()));
    }
}
