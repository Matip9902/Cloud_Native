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
            stockNotification.setMessage("Notebook Lenovo ThinkPad E14 con stock bajo: 8 unidades.");
            stockNotification.setReadStatus(false);

            Notification orderNotification = new Notification();
            orderNotification.setRecipient("operaciones");
            orderNotification.setMessage("Catalogo de proveedores actualizado correctamente.");
            orderNotification.setReadStatus(true);

            Notification priceNotification = new Notification();
            priceNotification.setRecipient("ventas");
            priceNotification.setMessage("Precio del monitor Samsung actualizado para la campana mensual.");
            priceNotification.setReadStatus(false);

            notificationRepository.save(stockNotification);
            notificationRepository.save(orderNotification);
            notificationRepository.save(priceNotification);
        };
    }
}
