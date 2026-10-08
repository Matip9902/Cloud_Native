package cl.duoc.cloudnative.rabbitadmin.controller;

import cl.duoc.cloudnative.rabbitadmin.dto.BindingRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.BindingResponse;
import cl.duoc.cloudnative.rabbitadmin.service.RabbitBindingAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bindings")
public class RabbitBindingAdminController {

    private final RabbitBindingAdminService bindingAdminService;

    public RabbitBindingAdminController(RabbitBindingAdminService bindingAdminService) {
        this.bindingAdminService = bindingAdminService;
    }

    @PostMapping
    ResponseEntity<BindingResponse> create(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bindingAdminService.create(request));
    }

    @DeleteMapping
    ResponseEntity<Void> delete(@Valid @RequestBody BindingRequest request) {
        bindingAdminService.delete(request);
        return ResponseEntity.noContent().build();
    }
}
