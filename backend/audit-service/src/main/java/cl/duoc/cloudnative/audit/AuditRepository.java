package cl.duoc.cloudnative.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditRecord, UUID> {}
