package cl.duoc.cloudnative.supplier.service;

import cl.duoc.cloudnative.supplier.model.Supplier;
import cl.duoc.cloudnative.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public List<Supplier> findAll() {
        return repository.findAll();
    }

    public Supplier findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Proveedor no encontrado"));
    }

    public Supplier save(Supplier supplier) {
        return repository.save(supplier);
    }

    public Supplier update(Long id, Supplier request) {
        Supplier supplier = findById(id);
        supplier.setCompanyName(request.getCompanyName());
        supplier.setContactEmail(request.getContactEmail());
        supplier.setPhone(request.getPhone());
        return repository.save(supplier);
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
