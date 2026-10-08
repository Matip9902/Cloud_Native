package cl.duoc.cloudnative.rabbitadmin.service;

public class QueueNotFoundException extends RuntimeException {

    public QueueNotFoundException(String name) {
        super("No existe la cola: " + name);
    }
}
