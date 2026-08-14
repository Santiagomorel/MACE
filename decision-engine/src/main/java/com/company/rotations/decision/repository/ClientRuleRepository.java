package com.company.rotations.decision.repository;

import com.company.rotations.models.ClientRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRuleRepository extends JpaRepository<ClientRule, UUID> {

    @Query("SELECT c FROM ClientRule c WHERE c.tenantId = :tenantId AND c.active = true ORDER BY c.version DESC")
    Optional<ClientRule> findActiveByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT MAX(c.version) FROM ClientRule c WHERE c.tenantId = :tenantId")
    Integer findMaxVersionByTenantId(@Param("tenantId") String tenantId);

    List<ClientRule> findByTenantIdOrderByVersionDesc(String tenantId);

    @Query("SELECT c FROM ClientRule c WHERE c.tenantId = :tenantId ORDER BY c.version DESC")
    List<ClientRule> findByTenantIdOrderByVersionDescWithInactive(@Param("tenantId") String tenantId);
}
