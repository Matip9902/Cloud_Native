package cl.duoc.cloudnative.customer.repository;

import cl.duoc.cloudnative.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
