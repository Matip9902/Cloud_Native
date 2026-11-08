package cl.duoc.cloudnative.audit;

import cl.duoc.cloudnative.audit.messaging.ProductEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class AuditService {
    private static final Set<String> SUPPORTED = Set.of("PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED");
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(ProductEvent event) {
        if (event == null || event.eventId() == null || event.eventType() == null
                || !SUPPORTED.contains(event.eventType()) || event.productId() == null
                || event.productName() == null || event.productName().isBlank() || event.occurredAt() == null) {
            throw new IllegalArgumentException("Evento de auditoria invalido");
        }
        if (!repository.existsById(event.eventId())) {
            repository.save(new AuditRecord(event));
        }
    }
}
