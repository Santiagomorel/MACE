package com.company.rotations.verification.repository;

import com.company.rotations.verification.model.VerificationResultEntity;
import com.company.rotations.verification.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationResultRepository extends JpaRepository<VerificationResultEntity, String> {

    List<VerificationResultEntity> findByEventId(String eventId);

    List<VerificationResultEntity> findByAccountId(String accountId);

    List<VerificationResultEntity> findByTenantId(String tenantId);

    List<VerificationResultEntity> findByStatus(VerificationStatus status);

    List<VerificationResultEntity> findByStatusAndTenantId(VerificationStatus status, String tenantId);

    List<VerificationResultEntity> findByTenantIdAndStatus(String tenantId, VerificationStatus status);

    @Query("SELECT v FROM VerificationResultEntity v WHERE v.verifiedAt < :cutoff")
    List<VerificationResultEntity> findByVerifiedBefore(@Param("cutoff") Instant cutoff);

    @Query("DELETE FROM VerificationResultEntity v WHERE v.tenantId = :tenantId AND v.verifiedAt < :cutoff")
    void deleteByTenantIdAndTimestampBefore(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);

    long countByTenantId(String tenantId);

    long countByStatus(VerificationStatus status);
}
