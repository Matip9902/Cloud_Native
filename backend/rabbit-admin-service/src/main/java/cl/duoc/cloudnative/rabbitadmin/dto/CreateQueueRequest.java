package cl.duoc.cloudnative.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateQueueRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$",
                message = "El nombre solo admite letras, numeros, puntos, guiones y guion bajo"
        )
        String name,
        boolean durable
) {
}
