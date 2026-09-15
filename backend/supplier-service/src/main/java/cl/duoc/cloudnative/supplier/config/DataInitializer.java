package cl.duoc.cloudnative.supplier.config;

import cl.duoc.cloudnative.supplier.model.Supplier;
import cl.duoc.cloudnative.supplier.repository.SupplierRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedSuppliers(SupplierRepository supplierRepository) {
        return args -> {
            if (supplierRepository.count() > 0) {
                return;
            }

            Supplier hardwareSupplier = new Supplier();
            hardwareSupplier.setCompanyName("TechParts Chile");
            hardwareSupplier.setContactEmail("ventas@techparts.example.com");
            hardwareSupplier.setPhone("+56 2 2555 0101");

            Supplier logisticsSupplier = new Supplier();
            logisticsSupplier.setCompanyName("Logistica Andina");
            logisticsSupplier.setContactEmail("contacto@logisticaandina.example.com");
            logisticsSupplier.setPhone("+56 2 2666 0202");

            supplierRepository.save(hardwareSupplier);
            supplierRepository.save(logisticsSupplier);
        };
    }
}
