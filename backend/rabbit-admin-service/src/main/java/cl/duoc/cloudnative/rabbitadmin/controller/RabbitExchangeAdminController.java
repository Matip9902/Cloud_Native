package cl.duoc.cloudnative.rabbitadmin.controller;

import cl.duoc.cloudnative.rabbitadmin.dto.CreateExchangeRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.ExchangeResponse;
import cl.duoc.cloudnative.rabbitadmin.service.RabbitExchangeAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/exchanges")
public class RabbitExchangeAdminController {

    private final RabbitExchangeAdminService exchangeAdminService;

    public RabbitExchangeAdminController(RabbitExchangeAdminService exchangeAdminService) {
        this.exchangeAdminService = exchangeAdminService;
    }

    @PostMapping
    ResponseEntity<ExchangeResponse> create(@Valid @RequestBody CreateExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exchangeAdminService.create(request));
    }

    @DeleteMapping("/{name}")
    ResponseEntity<Void> delete(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$", message = "Nombre de exchange invalido")
            String name
    ) {
        exchangeAdminService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
