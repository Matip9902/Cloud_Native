package cl.duoc.cloudnative.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BindingRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$", message = "El nombre del exchange es invalido")
        String exchange,
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$", message = "El nombre de la cola es invalido")
        String queue,
        @NotNull(message = "La routing key es obligatoria")
        @Pattern(regexp = "^[a-zA-Z0-9.*#_-]*$", message = "La routing key es invalida")
        String routingKey
) {
}
