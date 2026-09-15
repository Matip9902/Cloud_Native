package cl.duoc.cloudnative.inventory.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        Integer stock,
        boolean active,
        OffsetDateTime createdAt
) {
}
