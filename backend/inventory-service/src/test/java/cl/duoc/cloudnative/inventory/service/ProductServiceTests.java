package cl.duoc.cloudnative.inventory.service;

import cl.duoc.cloudnative.inventory.dto.ProductRequest;
import cl.duoc.cloudnative.inventory.messaging.ProductEvent;
import cl.duoc.cloudnative.inventory.messaging.ProductEventPublisher;
import cl.duoc.cloudnative.inventory.model.Product;
import cl.duoc.cloudnative.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository repository;

    @Mock
    private ProductEventPublisher publisher;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository, publisher);
    }

    @Test
    void createPublishesProductCreatedEvent() {
        Product saved = product(10L, "Mouse Gamer");
        when(repository.save(org.mockito.ArgumentMatchers.any(Product.class))).thenReturn(saved);

        service.create(new ProductRequest("Mouse Gamer", "Accesorios", new BigDecimal("29990"), 5, true));

        ArgumentCaptor<ProductEvent> event = ArgumentCaptor.forClass(ProductEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("PRODUCT_CREATED");
        assertThat(event.getValue().productId()).isEqualTo(10L);
        assertThat(event.getValue().productName()).isEqualTo("Mouse Gamer");
    }

    @Test
    void deletePublishesProductDeletedEvent() {
        Product product = product(12L, "Teclado");
        when(repository.findById(12L)).thenReturn(Optional.of(product));

        service.delete(12L);

        verify(repository).delete(product);
        ArgumentCaptor<ProductEvent> event = ArgumentCaptor.forClass(ProductEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("PRODUCT_DELETED");
        assertThat(event.getValue().productId()).isEqualTo(12L);
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory("Accesorios");
        product.setPrice(new BigDecimal("29990"));
        product.setStock(5);
        product.setActive(true);
        return product;
    }
}
