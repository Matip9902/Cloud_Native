package cl.duoc.cloudnative.analytics;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MetricRepository extends JpaRepository<ProductMetric, UUID> {}
