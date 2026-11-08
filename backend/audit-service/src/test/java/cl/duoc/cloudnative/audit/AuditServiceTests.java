package cl.duoc.cloudnative.audit;

import cl.duoc.cloudnative.audit.messaging.ProductEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(AuditService.class)
class AuditServiceTests {
    @Autowired AuditService service;
    @Autowired AuditRepository repository;

    @Test
    void savesEventDetailsAndDeduplicatesRedeliveries() {
        ProductEvent event = new ProductEvent(UUID.randomUUID(), "PRODUCT_CREATED", 12L, "Monitor", OffsetDateTime.now());
        service.record(event);
        service.record(event);
        assertThat(repository.count()).isEqualTo(1);
        AuditRecord saved = repository.findById(event.eventId()).orElseThrow();
        assertThat(saved.productName).isEqualTo("Monitor");
        assertThat(saved.productId).isEqualTo(12L);
        assertThat(saved.eventType).isEqualTo("PRODUCT_CREATED");
        assertThat(saved.receivedAt).isNotNull();
    }

    @Test
    void rejectsUnknownTypesWithoutSaving() {
        ProductEvent event = new ProductEvent(UUID.randomUUID(), "UNKNOWN", 12L, "Monitor", OffsetDateTime.now());
        assertThatThrownBy(() -> service.record(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsIncompleteEventsWithoutSaving() {
        ProductEvent event = new ProductEvent(null, "PRODUCT_CREATED", 12L, "Monitor", OffsetDateTime.now());
        assertThatThrownBy(() -> service.record(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }
}
