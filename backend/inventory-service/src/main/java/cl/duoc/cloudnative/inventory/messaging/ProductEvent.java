package cl.duoc.cloudnative.inventory.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductEvent(
        UUID eventId,
        String eventType,
        Long productId,
        String productName,
        OffsetDateTime occurredAt
) {
    public static ProductEvent of(String eventType, Long productId, String productName) {
        return new ProductEvent(UUID.randomUUID(), eventType, productId, productName, OffsetDateTime.now());
    }
}
