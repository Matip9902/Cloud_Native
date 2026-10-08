package cl.duoc.cloudnative.rabbitadmin.dto;

public record ExchangeResponse(String name, String type, boolean durable, String message) {
}
