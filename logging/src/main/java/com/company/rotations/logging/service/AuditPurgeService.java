package com.company.rotations.logging.service;

import com.company.rotations.logging.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AuditPurgeService.class);

    private final AuditEventRepository repository;

    @Value("${app.logging.audit-retention-days:90}")
    private int auditRetentionDays;

    public AuditPurgeService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void purgeExpiredAuditEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(auditRetentionDays);
        long beforeCount = repository.countByCreatedAtBefore(cutoff);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        log.info("Audit purge completed: deleted {} events older than {} days (cutoff: {}). {} events remaining.",
                 deleted, auditRetentionDays, cutoff, beforeCount - deleted);
    }
}
