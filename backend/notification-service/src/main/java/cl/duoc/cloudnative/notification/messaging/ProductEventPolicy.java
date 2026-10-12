package cl.duoc.cloudnative.notification.messaging;

import java.util.Set;

final class ProductEventPolicy {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "PRODUCT_CREATED",
            "PRODUCT_UPDATED",
            "PRODUCT_DELETED"
    );

    private ProductEventPolicy() {
    }

    static void requireSupported(ProductEvent event) {
        if (!SUPPORTED_TYPES.contains(event.eventType())) {
            throw new IllegalArgumentException("Tipo de evento no soportado: " + event.eventType());
        }
    }
}
