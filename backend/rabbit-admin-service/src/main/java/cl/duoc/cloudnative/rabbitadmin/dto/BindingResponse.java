package cl.duoc.cloudnative.rabbitadmin.dto;

public record BindingResponse(String exchange, String queue, String routingKey, String message) {
}
