package cl.duoc.cloudnative.kafkaadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/kafka")
public class TopicController {
    private final AdminClient admin;
    private static final Set<String> ALLOWED_CONFIGS=Set.of("retention.ms","retention.bytes","cleanup.policy","min.insync.replicas");
    public TopicController(AdminClient admin) { this.admin=admin; }
    public record CreateTopic(@NotBlank @Pattern(regexp="[a-zA-Z0-9][a-zA-Z0-9._-]{0,248}") String name,
            @Min(1) @Max(100) int partitions, @Min(1) @Max(3) short replicationFactor,
            Map<String,String> configs) {}
    public record Partitions(@Min(1) @Max(100) int partitions) {}
    @GetMapping("/topics")
    public Set<String> list() throws Exception { return admin.listTopics().names().get(10,TimeUnit.SECONDS); }
    @GetMapping("/topics/{name}")
    public Map<String,Object> describe(@PathVariable String name) throws Exception {
        checkName(name);
        TopicDescription topic=admin.describeTopics(List.of(name)).allTopicNames().get(10,TimeUnit.SECONDS).get(name);
        ConfigResource resource=new ConfigResource(ConfigResource.Type.TOPIC,name);
        Map<String,String> configs=new TreeMap<>();
        admin.describeConfigs(List.of(resource)).all().get(10,TimeUnit.SECONDS).get(resource).entries()
                .stream().filter(e -> ALLOWED_CONFIGS.contains(e.name())).forEach(e -> configs.put(e.name(),e.value()));
        return Map.of("name",name,"configs",configs,"partitions",topic.partitions().stream().map(p -> Map.of(
                "partition",p.partition(),"leader",p.leader().id(),
                "replicas",p.replicas().stream().map(n -> n.id()).toList(),
                "isr",p.isr().stream().map(n -> n.id()).toList())).toList());
    }
    @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody CreateTopic request) throws Exception {
        checkName(request.name());
        Map<String,String> configs=validateConfigs(request.configs());
        admin.createTopics(List.of(new NewTopic(request.name(),request.partitions(),request.replicationFactor())
                .configs(configs))).all().get(10,TimeUnit.SECONDS);
    }
    @PatchMapping("/topics/{name}/config") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configure(@PathVariable String name,@RequestBody Map<String,String> configs) throws Exception {
        checkName(name);
        var operations=validateConfigs(configs).entrySet().stream().map(e -> new AlterConfigOp(
                new ConfigEntry(e.getKey(),e.getValue()),AlterConfigOp.OpType.SET)).toList();
        admin.incrementalAlterConfigs(Map.of(new ConfigResource(ConfigResource.Type.TOPIC,name),operations))
                .all().get(10,TimeUnit.SECONDS);
    }
    @PatchMapping("/topics/{name}/partitions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void partitions(@PathVariable String name,@Valid @RequestBody Partitions request) throws Exception {
        checkName(name);
        admin.createPartitions(Map.of(name,NewPartitions.increaseTo(request.partitions()))).all().get(10,TimeUnit.SECONDS);
    }
    @DeleteMapping("/topics/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) throws Exception {
        checkName(name);
        admin.deleteTopics(List.of(name)).all().get(10,TimeUnit.SECONDS);
    }
    @GetMapping("/consumer-groups")
    public List<Map<String,String>> groups() throws Exception {
        return admin.listConsumerGroups().all().get(10,TimeUnit.SECONDS).stream()
                .map(g -> Map.of("groupId",g.groupId(),"state",g.state().map(Enum::name).orElse("UNKNOWN"))).toList();
    }
    @GetMapping("/consumer-groups/{id}")
    public Map<String,Object> group(@PathVariable String id) throws Exception {
        var description=admin.describeConsumerGroups(List.of(id)).all().get(10,TimeUnit.SECONDS).get(id);
        var offsets=admin.listConsumerGroupOffsets(id).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);
        return Map.of("groupId",id,"state",description.state().toString(),"members",description.members().size(),
                "offsets",offsets.entrySet().stream().map(e -> Map.of("topic",e.getKey().topic(),
                        "partition",e.getKey().partition(),"offset",e.getValue().offset())).toList());
    }
    private void checkName(String name) {
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,248}") || name.startsWith("__"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or internal topic name");
    }
    private Map<String,String> validateConfigs(Map<String,String> configs) {
        if (configs==null) return Map.of();
        configs.forEach((key,value) -> {
            if (!ALLOWED_CONFIGS.contains(key) || value==null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported topic configuration: "+key);
            try {
                if (key.equals("cleanup.policy")) {
                    if (!Set.of("delete","compact","delete,compact","compact,delete").contains(value))
                        throw new IllegalArgumentException();
                } else {
                    long number=Long.parseLong(value);
                    if (key.equals("min.insync.replicas") ? number<1 || number>3 : number< -1)
                        throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid value for "+key);
            }
        });
        return configs;
    }
}
