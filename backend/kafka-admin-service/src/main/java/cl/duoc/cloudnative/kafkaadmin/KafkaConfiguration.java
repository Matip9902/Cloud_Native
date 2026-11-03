package cl.duoc.cloudnative.kafkaadmin;
import java.util.Map;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class KafkaConfiguration {
    @Bean(destroyMethod="close")
    AdminClient adminClient(KafkaProperties properties) {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                properties.getBootstrapServers(), AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,10000,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,10000));
    }
    @Bean org.springframework.kafka.core.KafkaAdmin.NewTopics topics(
            @Value("${KAFKA_PRODUCT_TOPIC:product.events}") String products,
            @Value("${KAFKA_METRICS_TOPIC:product.metrics}") String metrics) {
        return new org.springframework.kafka.core.KafkaAdmin.NewTopics(
            topic(products,604800000L), topic(metrics,2592000000L),
            topic(products+".DLT",604800000L), topic(metrics+".DLT",604800000L));
    }
    private NewTopic topic(String name,long retention) {
        return new NewTopic(name,3,(short)3).configs(Map.of(TopicConfig.RETENTION_MS_CONFIG,Long.toString(retention),
                TopicConfig.CLEANUP_POLICY_CONFIG,"delete",TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,"2"));
    }
}
