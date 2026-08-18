package com.company.rotations.decision.repository;

import com.company.rotations.models.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    List<Credential> findByTenantIdAndStatus(String tenantId, Credential.CredentialStatus status);
    Optional<Credential> findByTenantIdAndKeyId(String tenantId, String keyId);
    List<Credential> findByTenantId(String tenantId);
}
