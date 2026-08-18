package com.company.rotations.verification.repository;

import com.company.rotations.logging.service.AuditPurgeService;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.verification.model.VerificationResultEntity;
import com.company.rotations.verification.model.VerificationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@EntityScan(basePackages = "com.company.rotations.verification.model")
@DisplayName("VerificationResultRepository Integration Tests")
class VerificationResultRepositoryIntegrationTest {

    @Autowired
    private VerificationResultRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @MockBean
    private AuditService auditService;

    @MockBean
    private AuditPurgeService auditPurgeService;

    @MockBean
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Should save and retrieve verification result")
    void shouldSaveAndRetrieveVerificationResult() {
        VerificationResultEntity entity = new VerificationResultEntity(
                "123456789012", "arn:aws:iam::123456789012:user/test",
                VerificationStatus.VERIFIED, Set.of("s3:GetObject"),
                "2024-01-15", "STS verification successful",
                "evt-001", "tenant-1");

        VerificationResultEntity saved = repository.save(entity);
        entityManager.flush();

        assertNotNull(saved.getId());
        assertEquals("123456789012", saved.getAccountId());

        VerificationResultEntity found = repository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
    }

    @Test
    @DisplayName("Should find by event ID")
    void shouldFindByEventId() {
        String eventId = "evt-" + UUID.randomUUID();

        VerificationResultEntity entity = new VerificationResultEntity(
                "987654321098", "arn:aws:iam::987654321098:user/admin",
                VerificationStatus.INVALID, Set.of(),
                "never", "Invalid credentials",
                eventId, "tenant-1");

        repository.save(entity);
        entityManager.flush();

        List<VerificationResultEntity> results = repository.findByEventId(eventId);

        assertEquals(1, results.size());
        assertEquals(eventId, results.get(0).getEventId());
    }

    @Test
    @DisplayName("Should find by account ID")
    void shouldFindByAccountId() {
        String accountId = "111222333444";

        VerificationResultEntity entity1 = new VerificationResultEntity(
                accountId, "arn:aws:iam::111222333444:user/user1",
                VerificationStatus.VERIFIED, Set.of("s3:ListBucket"),
                "2024-01-15", null,
                "evt-1", "tenant-1");

        VerificationResultEntity entity2 = new VerificationResultEntity(
                accountId, "arn:aws:iam::111222333444:user/user2",
                VerificationStatus.INVALID, Set.of(),
                "never", "Expired credentials",
                "evt-2", "tenant-1");

        repository.save(entity1);
        repository.save(entity2);
        entityManager.flush();

        List<VerificationResultEntity> results = repository.findByAccountId(accountId);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.getAccountId().equals(accountId)));
    }

    @Test
    @DisplayName("Should find by tenant ID")
    void shouldFindByTenantId() {
        String tenantId = "tenant-2";

        for (int i = 0; i < 3; i++) {
            VerificationResultEntity entity = new VerificationResultEntity(
                    "account-" + i, null, VerificationStatus.VERIFIED, Set.of("ec2:DescribeInstances"),
                    "2024-01-15", null,
                    "evt-" + i, tenantId);
            repository.save(entity);
        }

        entityManager.flush();

        List<VerificationResultEntity> results = repository.findByTenantId(tenantId);

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(r -> tenantId.equals(r.getTenantId())));
    }

    @Test
    @DisplayName("Should find by status and tenant")
    void shouldFindByStatusAndTenant() {
        String tenantId = "tenant-test";

        VerificationResultEntity verified = new VerificationResultEntity(
                "account-1", null, VerificationStatus.VERIFIED, Set.of("s3:GetObject"),
                "2024-01-15", null, "evt-1", tenantId);

        VerificationResultEntity invalid = new VerificationResultEntity(
                "account-2", null, VerificationStatus.INVALID, Set.of(),
                "never", "Access denied", "evt-2", tenantId);

        VerificationResultEntity rateLimited = new VerificationResultEntity(
                "account-3", null, VerificationStatus.RATE_LIMITED, Set.of(),
                "unknown", "Rate limit exceeded", "evt-3", tenantId);

        repository.save(verified);
        repository.save(invalid);
        repository.save(rateLimited);
        entityManager.flush();

        List<VerificationResultEntity> verifiedResults = repository.findByStatusAndTenantId(
                VerificationStatus.VERIFIED, tenantId);
        List<VerificationResultEntity> invalidResults = repository.findByStatusAndTenantId(
                VerificationStatus.INVALID, tenantId);

        assertEquals(1, verifiedResults.size());
        assertEquals(VerificationStatus.VERIFIED, verifiedResults.get(0).getStatus());
        assertEquals(1, invalidResults.size());
        assertEquals(VerificationStatus.INVALID, invalidResults.get(0).getStatus());
    }

//     @Test
//     @DisplayName("Should delete by tenant and older than")
//     void shouldDeleteByTenantAndOlderThan() {
//         String tenantId = "tenant-purge";

//         VerificationResultEntity oldEntity = new VerificationResultEntity(
//                 "old-account", null, VerificationStatus.INVALID, Set.of(),
//                 "never", "Old failure", "evt-old", tenantId);
//         oldEntity.setVerifiedAt(Instant.now().minusSeconds(7200));

//         VerificationResultEntity recentEntity = new VerificationResultEntity(
//                 "recent-account", null, VerificationStatus.VERIFIED, Set.of("s3:GetObject"),
//                 "2024-01-15", null, "evt-recent", tenantId);
//         recentEntity.setVerifiedAt(Instant.now());

//         repository.save(oldEntity);
//         repository.save(recentEntity);
//         entityManager.flush();

//         repository.deleteByTenantIdAndTimestampBefore(tenantId, Instant.now());
//         entityManager.flush();

//         List<VerificationResultEntity> remaining = repository.findByTenantId(tenantId);
//         assertEquals(1, remaining.size());
//         assertEquals("recent-account", remaining.get(0).getAccountId());
//     }

    @Test
    @DisplayName("Should count by tenant ID")
    void shouldCountByTenantId() {
        String tenantId = "tenant-count";

        for (int i = 0; i < 5; i++) {
            VerificationResultEntity entity = new VerificationResultEntity(
                    "account-" + i, null, VerificationStatus.VERIFIED, Set.of(),
                    "2024-01-15", null, "evt-" + i, tenantId);
            repository.save(entity);
        }

        entityManager.flush();

        long count = repository.countByTenantId(tenantId);
        assertEquals(5, count);
    }

    @Test
    @DisplayName("Should persist and retrieve action matrix")
    void shouldPersistActionMatrix() {
        Set<String> actions = Set.of("s3:GetObject", "s3:ListBucket", "ec2:DescribeInstances");

        VerificationResultEntity entity = new VerificationResultEntity(
                "matrix-account", "arn:aws:iam::matrix:user/test",
                VerificationStatus.VERIFIED, actions,
                "2024-01-15", "Enumerated permissions",
                "evt-matrix", "tenant-matrix");

        VerificationResultEntity saved = repository.save(entity);
        entityManager.flush();

        VerificationResultEntity found = repository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertNotNull(found.getActionMatrix());
        assertEquals(actions.size(), found.getActionMatrix().size());
        assertTrue(found.getActionMatrix().containsAll(actions));
    }

    @Test
    @DisplayName("Should handle empty action matrix")
    void shouldHandleEmptyActionMatrix() {
        VerificationResultEntity entity = new VerificationResultEntity(
                "empty-account", null,
                VerificationStatus.INVALID, Set.of(),
                "never", "No permissions found",
                "evt-empty", "tenant-empty");

        VerificationResultEntity saved = repository.save(entity);
        entityManager.flush();

        VerificationResultEntity found = repository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertNotNull(found.getActionMatrix());
        assertTrue(found.getActionMatrix().isEmpty());
    }

    @Test
    @DisplayName("Should convert entity to domain model")
    void shouldConvertEntityToDomain() {
        VerificationResultEntity entity = new VerificationResultEntity(
                "domain-account", "arn:aws:iam::domain:user/test",
                VerificationStatus.VERIFIED, Set.of("s3:GetObject"),
                "2024-01-15", "Domain conversion test",
                "evt-domain", "tenant-domain");

        com.company.rotations.verification.model.VerificationResult domain = entity.toDomain();

        assertNotNull(domain);
        assertEquals("domain-account", domain.getAccountId());
        assertEquals("arn:aws:iam::domain:user/test", domain.getIdentityArn());
        assertEquals(VerificationStatus.VERIFIED, domain.getStatus());
        assertEquals(1, domain.getActionMatrix().size());
        assertTrue(domain.getActionMatrix().contains("s3:GetObject"));
    }
}
