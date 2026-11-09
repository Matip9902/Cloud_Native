package cl.duoc.cloudnative.kafkaadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Standalone MVC exercises binding, validation and HTTP error mapping without brokers or Azure.
@ExtendWith(MockitoExtension.class)
class TopicControllerTests {
    @Mock AdminClient admin;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ROOT = "/api/v1/kafka";
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TopicController(admin))
                .setControllerAdvice(new KafkaApiErrors()).build();
    }
    private static <T> KafkaFuture<T> failed(Throwable exception) {
        var future = new KafkaFutureImpl<T>(); future.completeExceptionally(exception); return future;
    }
    private String creation(String name, int partitions, int replicas) throws Exception {
        return mapper.writeValueAsString(Map.of("name", name, "partitions", partitions,
                "replicationFactor", replicas, "configs", Map.of("retention.ms", "86400000")));
    }

    @Test void listsActualAdminResult() throws Exception {
        ListTopicsResult result = mock(ListTopicsResult.class);
        when(admin.listTopics()).thenReturn(result);
        when(result.names()).thenReturn(KafkaFuture.completedFuture(Set.of("product.events", "product.metrics")));
        mvc.perform(get(ROOT + "/topics")).andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.containsInAnyOrder("product.events", "product.metrics")));
    }
    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void createsTopicWithRequestedTopologyAndRetention() throws Exception {
        CreateTopicsResult result = mock(CreateTopicsResult.class);
        when(admin.createTopics(anyCollection())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        mvc.perform(post(ROOT + "/topics").contentType(MediaType.APPLICATION_JSON)
                .content(creation("demo.unit", 3, 3))).andExpect(status().isCreated());
        ArgumentCaptor<Collection<NewTopic>> topics = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(topics.capture());
        NewTopic created = topics.getValue().iterator().next();
        assertThat(created.name()).isEqualTo("demo.unit");
        assertThat(created.numPartitions()).isEqualTo(3);
        assertThat(created.replicationFactor()).isEqualTo((short) 3);
        assertThat(created.configs()).containsEntry("retention.ms", "86400000");
    }
    @ParameterizedTest @CsvSource({"demo.unit,0,3", "demo.unit,3,4", "bad/name,3,3"})
    void invalidCreationDoesNotContactBroker(String name, int partitions, int replicas) throws Exception {
        mvc.perform(post(ROOT + "/topics").contentType(MediaType.APPLICATION_JSON)
                .content(creation(name, partitions, replicas))).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test void describesReplicasIsrAndOnlySupportedConfigurations() throws Exception {
        Node node = new Node(1, "kafka-1", 9092);
        var topic = new TopicDescription("demo.unit", false,
                List.of(new TopicPartitionInfo(0, node, List.of(node), List.of(node))));
        DescribeTopicsResult topics = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(List.of("demo.unit"))).thenReturn(topics);
        when(topics.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of("demo.unit", topic)));
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, "demo.unit");
        DescribeConfigsResult configs = mock(DescribeConfigsResult.class);
        when(admin.describeConfigs(List.of(resource))).thenReturn(configs);
        when(configs.all()).thenReturn(KafkaFuture.completedFuture(Map.of(resource,
                new Config(List.of(new ConfigEntry("retention.ms", "86400000"), new ConfigEntry("segment.ms", "1000"))))));
        mvc.perform(get(ROOT + "/topics/demo.unit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions[0].leader").value(1))
                .andExpect(jsonPath("$.partitions[0].replicas[0]").value(1))
                .andExpect(jsonPath("$.partitions[0].isr[0]").value(1))
                .andExpect(jsonPath("$.configs['retention.ms']").value("86400000"))
                .andExpect(jsonPath("$.configs['segment.ms']").doesNotExist());
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void patchesConfigurationWithIncrementalSetOperation() throws Exception {
        AlterConfigsResult result = mock(AlterConfigsResult.class);
        when(admin.incrementalAlterConfigs(anyMap())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        mvc.perform(patch(ROOT + "/topics/demo.unit/config").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retention.ms\":\"172800000\"}")).andExpect(status().isNoContent());
        ArgumentCaptor<Map<ConfigResource, Collection<AlterConfigOp>>> changes = ArgumentCaptor.forClass(Map.class);
        verify(admin).incrementalAlterConfigs(changes.capture());
        var operations = changes.getValue().get(new ConfigResource(ConfigResource.Type.TOPIC, "demo.unit"));
        assertThat(operations).hasSize(1);
        AlterConfigOp operation = operations.iterator().next();
        assertThat(operation.opType()).isEqualTo(AlterConfigOp.OpType.SET);
        assertThat(operation.configEntry().name()).isEqualTo("retention.ms");
        assertThat(operation.configEntry().value()).isEqualTo("172800000");
    }
    @ParameterizedTest @CsvSource({"retention.ms,-2", "retention.ms,abc", "min.insync.replicas,0",
            "min.insync.replicas,4", "cleanup.policy,unknown", "segment.ms,1000"})
    void invalidConfigurationIsRejectedBeforeBrokerCall(String key, String value) throws Exception {
        mvc.perform(patch(ROOT + "/topics/demo.unit/config").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(key, value)))).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test void nullConfigurationValueIsRejected() throws Exception {
        mvc.perform(patch(ROOT + "/topics/demo.unit/config").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retention.ms\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void increasesTotalPartitionCount() throws Exception {
        CreatePartitionsResult result = mock(CreatePartitionsResult.class);
        when(admin.createPartitions(anyMap())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        mvc.perform(patch(ROOT + "/topics/demo.unit/partitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partitions\":6}")).andExpect(status().isNoContent());
        ArgumentCaptor<Map<String, NewPartitions>> changes = ArgumentCaptor.forClass(Map.class);
        verify(admin).createPartitions(changes.capture());
        assertThat(changes.getValue().get("demo.unit").totalCount()).isEqualTo(6);
    }
    @Test void deletesOnlyRequestedTopic() throws Exception {
        DeleteTopicsResult result = mock(DeleteTopicsResult.class);
        when(admin.deleteTopics(List.of("demo.unit"))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        mvc.perform(delete(ROOT + "/topics/demo.unit")).andExpect(status().isNoContent());
        verify(admin).deleteTopics(List.of("demo.unit"));
    }
    @Test void refusesInternalTopicDeletionBeforeBrokerCall() throws Exception {
        mvc.perform(delete(ROOT + "/topics/__consumer_offsets")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test void listsConsumerGroupsAndHandlesUnknownState() throws Exception {
        ListConsumerGroupsResult result = mock(ListConsumerGroupsResult.class);
        when(admin.listConsumerGroups()).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(List.of(
                new ConsumerGroupListing("analytics", false, Optional.of(ConsumerGroupState.STABLE)),
                new ConsumerGroupListing("legacy", false))));
        mvc.perform(get(ROOT + "/consumer-groups")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value("analytics"))
                .andExpect(jsonPath("$[0].state").value("STABLE"))
                .andExpect(jsonPath("$[1].state").value("UNKNOWN"));
    }
    @Test void describesMembersAndCommittedOffsets() throws Exception {
        DescribeConsumerGroupsResult description = mock(DescribeConsumerGroupsResult.class);
        when(admin.describeConsumerGroups(List.of("analytics"))).thenReturn(description);
        var group = new ConsumerGroupDescription("analytics", false, List.of(mock(MemberDescription.class)),
                "range", ConsumerGroupState.STABLE, Node.noNode());
        when(description.all()).thenReturn(KafkaFuture.completedFuture(Map.of("analytics", group)));
        ListConsumerGroupOffsetsResult offsets = mock(ListConsumerGroupOffsetsResult.class);
        when(admin.listConsumerGroupOffsets("analytics")).thenReturn(offsets);
        when(offsets.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(
                Map.of(new TopicPartition("product.events", 2), new OffsetAndMetadata(19))));
        mvc.perform(get(ROOT + "/consumer-groups/analytics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.members").value(1))
                .andExpect(jsonPath("$.offsets[0].topic").value("product.events"))
                .andExpect(jsonPath("$.offsets[0].partition").value(2))
                .andExpect(jsonPath("$.offsets[0].offset").value(19));
    }
    @Test void duplicateTopicBecomesHttpConflict() throws Exception {
        CreateTopicsResult result = mock(CreateTopicsResult.class);
        when(admin.createTopics(anyCollection())).thenReturn(result);
        when(result.all()).thenReturn(failed(new TopicExistsException("Already exists")));
        mvc.perform(post(ROOT + "/topics").contentType(MediaType.APPLICATION_JSON)
                .content(creation("demo.unit", 3, 3))).andExpect(status().isConflict());
    }
    @Test void missingTopicBecomesHttpNotFound() throws Exception {
        DescribeTopicsResult result = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(List.of("missing"))).thenReturn(result);
        when(result.allTopicNames()).thenReturn(failed(new UnknownTopicOrPartitionException("Missing topic")));
        mvc.perform(get(ROOT + "/topics/missing")).andExpect(status().isNotFound());
    }
    @Test void partitionDecreaseRejectedByBrokerBecomesHttpBadRequest() throws Exception {
        CreatePartitionsResult result = mock(CreatePartitionsResult.class);
        when(admin.createPartitions(anyMap())).thenReturn(result);
        when(result.all()).thenReturn(failed(new InvalidPartitionsException("Cannot decrease partitions")));
        mvc.perform(patch(ROOT + "/topics/demo.unit/partitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partitions\":1}")).andExpect(status().isBadRequest());
    }
    @Test void unavailableBrokerBecomesHttpServiceUnavailable() throws Exception {
        ListTopicsResult result = mock(ListTopicsResult.class);
        when(admin.listTopics()).thenReturn(result);
        when(result.names()).thenReturn(failed(new org.apache.kafka.common.errors.TimeoutException("Broker unavailable")));
        mvc.perform(get(ROOT + "/topics")).andExpect(status().isServiceUnavailable());
    }
}
