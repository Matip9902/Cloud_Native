package cl.duoc.cloudnative.rabbitadmin.dto;

public record QueueResponse(String name, boolean durable, String message) {
}
