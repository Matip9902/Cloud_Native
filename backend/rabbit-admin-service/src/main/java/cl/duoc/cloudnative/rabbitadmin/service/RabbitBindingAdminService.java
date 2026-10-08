package cl.duoc.cloudnative.rabbitadmin.service;

import cl.duoc.cloudnative.rabbitadmin.dto.BindingRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.BindingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RabbitBindingAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitBindingAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitBindingAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public BindingResponse create(BindingRequest request) {
        Binding binding = binding(request);
        amqpAdmin.declareBinding(binding);
        log.info("Binding RabbitMQ creado: exchange={}, queue={}, routingKey={}",
                request.exchange(), request.queue(), request.routingKey());
        return new BindingResponse(
                request.exchange(), request.queue(), request.routingKey(), "Binding creado correctamente"
        );
    }

    public void delete(BindingRequest request) {
        amqpAdmin.removeBinding(binding(request));
        log.info("Binding RabbitMQ eliminado: exchange={}, queue={}, routingKey={}",
                request.exchange(), request.queue(), request.routingKey());
    }

    private Binding binding(BindingRequest request) {
        return new Binding(
                request.queue(),
                Binding.DestinationType.QUEUE,
                request.exchange(),
                request.routingKey(),
                Map.of()
        );
    }
}
