package cl.duoc.cloudnative.audit;

import cl.duoc.cloudnative.audit.messaging.ProductEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_audit_records")
public class AuditRecord {
    @Id
    public UUID eventId;
    @Column(nullable = false, length = 40)
    public String eventType;
    @Column(nullable = false)
    public Long productId;
    @Column(nullable = false, length = 120)
    public String productName;
    @Column(nullable = false)
    public OffsetDateTime occurredAt;
    @Column(nullable = false)
    public OffsetDateTime receivedAt;

    protected AuditRecord() {}

    public AuditRecord(ProductEvent event) {
        eventId = event.eventId();
        eventType = event.eventType();
        productId = event.productId();
        productName = event.productName();
        occurredAt = event.occurredAt();
        receivedAt = OffsetDateTime.now();
    }
}
