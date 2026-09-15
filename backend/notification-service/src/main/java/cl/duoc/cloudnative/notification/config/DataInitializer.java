package cl.duoc.cloudnative.notification.config;

import cl.duoc.cloudnative.notification.model.Notification;
import cl.duoc.cloudnative.notification.repository.NotificationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedNotifications(NotificationRepository notificationRepository) {
        return args -> {
            if (notificationRepository.count() > 0) {
                return;
            }

            Notification stockNotification = new Notification();
            stockNotification.setRecipient("inventario");
            stockNotification.setMessage("Producto con stock bajo requiere revision.");
            stockNotification.setReadStatus(false);

            Notification orderNotification = new Notification();
            orderNotification.setRecipient("operaciones");
            orderNotification.setMessage("Nueva actualizacion disponible para catalogo de proveedores.");
            orderNotification.setReadStatus(true);

            notificationRepository.save(stockNotification);
            notificationRepository.save(orderNotification);
        };
    }
}
