package cl.duoc.cloudnative.inventory.config;

import cl.duoc.cloudnative.inventory.model.Product;
import cl.duoc.cloudnative.inventory.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedProducts(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() > 0) {
                return;
            }

            productRepository.save(product("Notebook Lenovo ThinkPad E14", "Computacion", "749990.00", 8));
            productRepository.save(product("Monitor Samsung Essential S3 24", "Monitores", "109990.00", 15));
            productRepository.save(product("Teclado Logitech K120", "Accesorios", "14990.00", 30));
        };
    }

    private Product product(String name, String category, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setActive(true);
        return product;
    }
}
