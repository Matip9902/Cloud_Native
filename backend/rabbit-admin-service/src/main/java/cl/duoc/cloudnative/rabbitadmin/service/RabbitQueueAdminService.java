package cl.duoc.cloudnative.rabbitadmin.service;

import cl.duoc.cloudnative.rabbitadmin.dto.CreateQueueRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.QueueResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.stereotype.Service;

@Service
public class RabbitQueueAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitQueueAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitQueueAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public QueueResponse create(CreateQueueRequest request) {
        Queue queue = new Queue(request.name(), request.durable(), false, false);
        String declaredName = amqpAdmin.declareQueue(queue);
        log.info("Cola RabbitMQ creada: name={}, durable={}", declaredName, request.durable());
        return new QueueResponse(declaredName, request.durable(), "Cola creada correctamente");
    }

    public void delete(String name) {
        if (amqpAdmin.getQueueProperties(name) == null) {
            throw new QueueNotFoundException(name);
        }
        amqpAdmin.deleteQueue(name);
        log.info("Cola RabbitMQ eliminada: name={}", name);
    }
}
