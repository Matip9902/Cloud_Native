package cl.duoc.cloudnative.analytics;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {
    private final MetricRepository repository;
    public MetricsController(MetricRepository repository) { this.repository=repository; }
    @GetMapping
    public Map<String,Object> summary() {
        Map<String,Long> counts=new LinkedHashMap<>();
        counts.put("PRODUCT_CREATED",0L); counts.put("PRODUCT_UPDATED",0L); counts.put("PRODUCT_DELETED",0L);
        var metrics=repository.findAll();
        metrics.forEach(m -> counts.merge(m.eventType,1L,Long::sum));
        return Map.of("total",metrics.size(),"operations",counts);
    }
}
