package cl.duoc.cloudnative.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$",
                message = "El nombre del exchange es invalido"
        )
        String name,
        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(regexp = "^(direct|topic|fanout)$", message = "El tipo debe ser direct, topic o fanout")
        String type,
        boolean durable
) {
}
