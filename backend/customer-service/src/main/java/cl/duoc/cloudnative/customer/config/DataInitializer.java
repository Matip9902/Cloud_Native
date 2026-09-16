package cl.duoc.cloudnative.customer.config;

import cl.duoc.cloudnative.customer.model.Customer;
import cl.duoc.cloudnative.customer.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedCustomers(CustomerRepository customerRepository) {
        return args -> {
            if (customerRepository.count() > 0) {
                return;
            }

            Customer firstCustomer = new Customer();
            firstCustomer.setFullName("Camila Rojas");
            firstCustomer.setEmail("camila.rojas@cliente.test");
            firstCustomer.setPhone("+56 9 6123 4587");

            Customer secondCustomer = new Customer();
            secondCustomer.setFullName("Diego Morales");
            secondCustomer.setEmail("diego.morales@cliente.test");
            secondCustomer.setPhone("+56 9 7345 2198");

            Customer thirdCustomer = new Customer();
            thirdCustomer.setFullName("Valentina Soto");
            thirdCustomer.setEmail("valentina.soto@cliente.test");
            thirdCustomer.setPhone("+56 9 8456 3071");

            customerRepository.save(firstCustomer);
            customerRepository.save(secondCustomer);
            customerRepository.save(thirdCustomer);
        };
    }
}
