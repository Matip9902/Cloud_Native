package cl.duoc.cloudnative.rabbitadmin.service;

import cl.duoc.cloudnative.rabbitadmin.dto.CreateExchangeRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.ExchangeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

@Service
public class RabbitExchangeAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitExchangeAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitExchangeAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public ExchangeResponse create(CreateExchangeRequest request) {
        Exchange exchange = switch (request.type()) {
            case "direct" -> new DirectExchange(request.name(), request.durable(), false);
            case "topic" -> new TopicExchange(request.name(), request.durable(), false);
            case "fanout" -> new FanoutExchange(request.name(), request.durable(), false);
            default -> throw new IllegalArgumentException("Tipo de exchange no soportado");
        };
        amqpAdmin.declareExchange(exchange);
        log.info("Exchange RabbitMQ creado: name={}, type={}", request.name(), request.type());
        return new ExchangeResponse(request.name(), request.type(), request.durable(), "Exchange creado correctamente");
    }

    public void delete(String name) {
        if (!amqpAdmin.deleteExchange(name)) {
            throw new RabbitResourceNotFoundException("exchange", name);
        }
        log.info("Exchange RabbitMQ eliminado: name={}", name);
    }
}
