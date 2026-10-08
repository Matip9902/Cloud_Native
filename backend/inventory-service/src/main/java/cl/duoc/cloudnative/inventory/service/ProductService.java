package cl.duoc.cloudnative.inventory.service;

import cl.duoc.cloudnative.inventory.dto.ProductRequest;
import cl.duoc.cloudnative.inventory.dto.ProductResponse;
import cl.duoc.cloudnative.inventory.messaging.ProductEvent;
import cl.duoc.cloudnative.inventory.messaging.ProductEventPublisher;
import cl.duoc.cloudnative.inventory.model.Product;
import cl.duoc.cloudnative.inventory.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductEventPublisher productEventPublisher;

    public ProductService(ProductRepository productRepository, ProductEventPublisher productEventPublisher) {
        this.productRepository = productRepository;
        this.productEventPublisher = productEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(boolean onlyActive) {
        List<Product> products = onlyActive ? productRepository.findByActiveTrue() : productRepository.findAll();
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        Product savedProduct = productRepository.save(product);
        productEventPublisher.publish(ProductEvent.of(
                "PRODUCT_CREATED",
                savedProduct.getId(),
                savedProduct.getName()
        ));
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));
        applyRequest(product, request);
        productEventPublisher.publish(ProductEvent.of(
                "PRODUCT_UPDATED",
                product.getId(),
                product.getName()
        ));
        return toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));
        productRepository.delete(product);
        productEventPublisher.publish(ProductEvent.of(
                "PRODUCT_DELETED",
                product.getId(),
                product.getName()
        ));
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setActive(request.active() == null || request.active());
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                product.getCreatedAt()
        );
    }
}
