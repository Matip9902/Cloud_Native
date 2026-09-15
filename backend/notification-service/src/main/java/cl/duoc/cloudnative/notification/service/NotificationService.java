package cl.duoc.cloudnative.notification.service;

import cl.duoc.cloudnative.notification.model.Notification;
import cl.duoc.cloudnative.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public List<Notification> findAll(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return repository.findAll();
        }
        return repository.findByRecipient(recipient);
    }

    public Notification create(Notification notification) {
        notification.setReadStatus(false);
        return repository.save(notification);
    }

    public Notification markAsRead(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notificacion no encontrada"));
        notification.setReadStatus(true);
        return repository.save(notification);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
