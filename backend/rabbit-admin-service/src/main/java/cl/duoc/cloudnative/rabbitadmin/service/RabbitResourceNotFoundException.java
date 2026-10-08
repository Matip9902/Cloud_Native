package cl.duoc.cloudnative.rabbitadmin.service;

public class RabbitResourceNotFoundException extends RuntimeException {

    public RabbitResourceNotFoundException(String resource, String name) {
        super("No existe el " + resource + ": " + name);
    }
}
