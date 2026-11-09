package cl.duoc.cloudnative.kafkaadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Test void invalidConfigurationIsRejectedBeforeBrokerCall() throws Exception {
        String key = "retention.ms";
        String value = "-2";
        mvc.perform(patch(ROOT + "/topics/demo.unit/config").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(key, value)))).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test void deletesOnlyRequestedTopic() throws Exception {
        DeleteTopicsResult result = mock(DeleteTopicsResult.class);
        when(admin.deleteTopics(List.of("demo.unit"))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        mvc.perform(delete(ROOT + "/topics/demo.unit")).andExpect(status().isNoContent());
        verify(admin).deleteTopics(List.of("demo.unit"));
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
}
