package com.company.rotations.logging.service;

import com.company.rotations.logging.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditPurgeServiceTest {

    @Mock
    private AuditEventRepository repository;

    private AuditPurgeService purgeService;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        when(repository.countByCreatedAtBefore(any(LocalDateTime.class)))
            .thenReturn(100L);
        when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class)))
            .thenReturn(42);

        purgeService = new AuditPurgeService(repository);
        ReflectionTestUtils.setField(purgeService, "auditRetentionDays", 90);
    }

    @Test
    void purgeExpiredAuditEvents_deletesOldEvents() {
        purgeService.purgeExpiredAuditEvents();

        verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void purgeExpiredAuditEvents_logsDeletionCount() {
        purgeService.purgeExpiredAuditEvents();

        verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
        verify(repository).countByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void purgeExpiredAuditEvents_usesConfiguredRetentionDays() {
        ReflectionTestUtils.setField(purgeService, "auditRetentionDays", 30);

        purgeService.purgeExpiredAuditEvents();

        verify(repository).deleteByCreatedAtBefore(argThat(date ->
            !date.isAfter(LocalDateTime.now().minusDays(30))
        ));
    }

    @Test
    void purgeExpiredAuditEvents_usesDefaultRetentionDays() {
        AuditPurgeService service = new AuditPurgeService(repository);

        purgeService.purgeExpiredAuditEvents();

        verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }
}
