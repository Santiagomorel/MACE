package com.company.rotations.decision.service;

import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class DroolsRuleService {

    private static final Logger log = LoggerFactory.getLogger(DroolsRuleService.class);

    private final ClientRuleRepository clientRuleRepository;
    private final Cache<String, KieSessionHolder> kieSessionCache;
    private final ConcurrentHashMap<String, Integer> lastKnownVersions = new ConcurrentHashMap<>();

    @Value("${decision.drools.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${decision.drools.max-drl-size-bytes:51200}")
    private int maxDrlSizeBytes;

    public DroolsRuleService(ClientRuleRepository clientRuleRepository,
                               @Value("${decision.drools.cache-ttl-seconds:300}") long cacheTtlSeconds,
                               @Value("${decision.drools.max-drl-size-bytes:51200}") int maxDrlSizeBytes) {
        this.clientRuleRepository = clientRuleRepository;
        this.maxDrlSizeBytes = maxDrlSizeBytes;
        this.kieSessionCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .build();
    }

    public KieSession getSession(String tenantId) {
        return kieSessionCache.get(tenantId, tid -> {
            ClientRule activeRule = clientRuleRepository.findActiveByTenantId(tid)
                    .orElse(null);
            if (activeRule == null) {
                log.warn("No active rules found for tenant {}, creating empty session", tid);
                return new KieSessionHolder(createEmptySession(tid));
            }
            lastKnownVersions.put(tid, activeRule.getVersion());
            return new KieSessionHolder(createSessionFromDrl(activeRule.getDrlContent(), tid));
        }).getSession();
    }

    public boolean updateRulesForTenant(String tenantId, byte[] drlContent, String playbookId) {
        if (drlContent.length > maxDrlSizeBytes) {
            log.error("DRL content exceeds max size {} bytes for tenant {}", maxDrlSizeBytes, tenantId);
            return false;
        }

        if (validateDrl(drlContent) > 0) {
            log.error("DRL validation failed for tenant {}", tenantId);
            return false;
        }

        Integer currentVersion = clientRuleRepository.findMaxVersionByTenantId(tenantId);
        int newVersion = (currentVersion == null ? 0 : currentVersion) + 1;

        ClientRule newRule = new ClientRule();
        newRule.setTenantId(tenantId);
        newRule.setVersion(newVersion);
        newRule.setDrlContent(drlContent);
        newRule.setPlaybookId(playbookId);
        newRule.setActive(true);

        ClientRule saved = clientRuleRepository.save(newRule);

        invalidateCache(tenantId);
        lastKnownVersions.put(tenantId, newVersion);

        log.info("Updated rules for tenant {} to version {}", tenantId, newVersion);
        return true;
    }

    public boolean rollbackToLastValid(String tenantId) {
        List<ClientRule> rules = clientRuleRepository.findByTenantIdOrderByVersionDescWithInactive(tenantId);
        for (ClientRule rule : rules) {
            if (rule.isActive() && validateDrl(rule.getDrlContent()) > 0) {
                rule.setActive(false);
                clientRuleRepository.save(rule);
                continue;
            }
            if (rule.isActive()) {
                rule.setActive(true);
                clientRuleRepository.save(rule);
                invalidateCache(tenantId);
                lastKnownVersions.put(tenantId, rule.getVersion());
                log.info("Rolled back tenant {} to version {}", tenantId, rule.getVersion());
                return true;
            }
        }
        return false;
    }

    public int validateDrl(byte[] drlContent) {
        try {
            String drl = new String(drlContent, "UTF-8");
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();
            kfs.write("src/main/resources/rules.drl", drl);
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();

            var results = kieBuilder.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                for (Message msg : results.getMessages(Message.Level.ERROR)) {
                    log.error("DRL validation error: {} - {}", msg.getLevel(), msg.getText());
                }
                return (int) results.getMessages(Message.Level.ERROR).stream().count();
            }
            return 0;
        } catch (Exception e) {
            log.error("DRL validation exception: {}", e.getMessage());
            return 1;
        }
    }

    public void invalidateCache(String tenantId) {
        kieSessionCache.invalidate(tenantId);
    }

    public boolean shouldRefresh(String tenantId) {
        Integer currentVersion = clientRuleRepository.findMaxVersionByTenantId(tenantId);
        Integer lastKnown = lastKnownVersions.get(tenantId);
        return currentVersion == null || (lastKnown == null || currentVersion > lastKnown);
    }

    public int getDrlSizeBytes(String tenantId) {
        return clientRuleRepository.findActiveByTenantId(tenantId)
                .map(ClientRule::getDrlSizeBytes)
                .orElse(0);
    }

    public int getMaxDrlSizeBytes() {
        return maxDrlSizeBytes;
    }

    private KieSession createSessionFromDrl(byte[] drlContent, String tenantId) {
        try {
            String drl = new String(drlContent, "UTF-8");
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();
            kfs.write("src/main/resources/rules.drl", drl);
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
            KieModule kieModule = kieBuilder.getKieModule();
            KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
            return kieContainer.newKieSession();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create KieSession from DRL: " + e.getMessage(), e);
        }
    }

    private KieSession createEmptySession(String tenantId) {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        String drl = "package com.security.rules." + tenantId + ";\n" +
                "// Empty ruleset for tenant " + tenantId + "\n" +
                "import com.company.rotations.models.Severidad;\n" +
                "rule \"placeholder\"\n" +
                "  agenda-group \"rules_" + tenantId + "\"\n" +
                "  no-loop true\n" +
                "  lock-on-active true\n" +
                "  salience 40\n" +
                "  when\n" +
                "    Alert( tenantId == \"" + tenantId + "\" )\n" +
                "  then\n" +
                "    // No rules matched\n" +
                "end\n";
        kfs.write("src/main/resources/rules.drl", drl);
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        KieModule kieModule = kieBuilder.getKieModule();
        KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
        return kieContainer.newKieSession();
    }

    public static class KieSessionHolder {
        private final KieSession session;
        private final long lastAccessed;

        public KieSessionHolder(KieSession session) {
            this.session = session;
            this.lastAccessed = System.currentTimeMillis();
        }

        public KieSession getSession() { return session; }
        public long getLastAccessed() { return lastAccessed; }
    }
}
