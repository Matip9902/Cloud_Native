package cl.duoc.cloudnative.audit.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductEvent(
        UUID eventId,
        String eventType,
        Long productId,
        String productName,
        OffsetDateTime occurredAt
) {
}
