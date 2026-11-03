package cl.duoc.cloudnative.analytics;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
public class ProductMetric {
    @Id public UUID eventId;
    @Column(nullable=false) public String eventType;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false) public OffsetDateTime occurredAt;
    public ProductMetric() {}
    public ProductMetric(ProductEvent event) {
        eventId=event.eventId(); eventType=event.eventType(); productId=event.productId(); occurredAt=event.occurredAt();
    }
}
