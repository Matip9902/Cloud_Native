package cl.duoc.cloudnative.customer.service;

import cl.duoc.cloudnative.customer.model.Customer;
import cl.duoc.cloudnative.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public Customer findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
    }

    public Customer save(Customer customer) {
        return repository.save(customer);
    }

    public Customer update(Long id, Customer request) {
        Customer customer = findById(id);
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        return repository.save(customer);
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
