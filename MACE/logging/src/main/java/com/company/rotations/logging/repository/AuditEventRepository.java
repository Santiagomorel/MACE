package com.company.rotations.logging.repository;

import com.company.rotations.logging.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    int deleteByCreatedAtBefore(LocalDateTime date);

    List<AuditEvent> findByClientId(String clientId);

    List<AuditEvent> findByAlertId(String alertId);

    List<AuditEvent> findByEventType(AuditEvent.AuditEventType eventType);

    long countByCreatedAtBefore(LocalDateTime date);

    long countByEventType(AuditEvent.AuditEventType eventType);
}
