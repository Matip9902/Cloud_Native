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
            hardwareSupplier.setCompanyName("TecnoSuministros SpA");
            hardwareSupplier.setContactEmail("ventas@tecnosuministros.test");
            hardwareSupplier.setPhone("+56 2 2450 1180");

            Supplier logisticsSupplier = new Supplier();
            logisticsSupplier.setCompanyName("Distribuidora Andes Ltda");
            logisticsSupplier.setContactEmail("contacto@distribuidoraandes.test");
            logisticsSupplier.setPhone("+56 2 2680 2240");

            supplierRepository.save(hardwareSupplier);
            supplierRepository.save(logisticsSupplier);
        };
    }
}
