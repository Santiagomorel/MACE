package com.company.rotations.verification.validator;

import com.company.rotations.verification.account.mapper.AccountMapper;
import com.company.rotations.verification.cache.VerificationCacheService;
import com.company.rotations.verification.config.CircuitBreakerService;
import com.company.rotations.verification.model.ProviderType;
import com.company.rotations.verification.model.VerificationResult;
import com.company.rotations.verification.provider.ProviderDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CredentialValidatorService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialValidatorService.class);

    private final ProviderDetector providerDetector;
    private final AccountMapper accountMapper;
    private final AwsCredentialValidator awsValidator;
    private final AzureCredentialValidator azureValidator;
    private final GcpCredentialValidator gcpValidator;
    private final VerificationCacheService cacheService;
    private final CircuitBreakerService circuitBreakerService;

    public CredentialValidatorService(ProviderDetector providerDetector,
                                       AccountMapper accountMapper,
                                       AwsCredentialValidator awsValidator,
                                       AzureCredentialValidator azureValidator,
                                       GcpCredentialValidator gcpValidator,
                                       VerificationCacheService cacheService,
                                       CircuitBreakerService circuitBreakerService) {
        this.providerDetector = providerDetector;
        this.accountMapper = accountMapper;
        this.awsValidator = awsValidator;
        this.azureValidator = azureValidator;
        this.gcpValidator = gcpValidator;
        this.cacheService = cacheService;
        this.circuitBreakerService = circuitBreakerService;
    }

    public VerificationResult verifyCredential(String accountHint,
                                                String credentialValue,
                                                String credentialId,
                                                Map<String, Object> rawPayload,
                                                List<String> knownAccountIds) {
        logger.info("Starting credential verification for credential: {}", credentialId);

        String cacheKey = buildCacheKey(accountHint, credentialValue);
        VerificationResult cached = cacheService.get(cacheKey);
        if (cached != null) {
            logger.info("Cache hit for credential: {}, returning cached result", credentialId);
            return cached;
        }

        ProviderType providerType = providerDetector.detectProvider(
                extractProviderName(rawPayload), credentialValue);

        if (providerType == ProviderType.UNKNOWN) {
            VerificationResult result = VerificationResult.unknownAccount(credentialId);
            cacheService.put(cacheKey, result);
            return result;
        }

        String accountId = accountMapper.mapAccount(accountHint, credentialId, knownAccountIds, providerType);
        if (accountId == null) {
            VerificationResult result = VerificationResult.unknownAccount(credentialId);
            cacheService.put(cacheKey, result);
            return result;
        }

        VerificationResult result = verifyWithProvider(providerType, accountId, credentialId, credentialValue);
        cacheService.put(cacheKey, result);
        return result;
    }

    private VerificationResult verifyWithProvider(ProviderType providerType, String accountId,
                                                   String credentialId, String credentialValue) {
        switch (providerType) {
            case AWS -> {
                if (circuitBreakerService.isAwsCircuitOpen()) {
                    logger.warn("AWS circuit breaker is open, returning CIRCUIT_OPEN for {}", credentialId);
                    return VerificationResult.failed(accountId, credentialId, "Circuit breaker open for AWS");
                }
                return awsValidator.validate(accountId, credentialId, credentialValue);
            }
            case AZURE -> {
                if (circuitBreakerService.isAzureCircuitOpen()) {
                    logger.warn("Azure circuit breaker is open, returning CIRCUIT_OPEN for {}", credentialId);
                    return VerificationResult.failed(accountId, credentialId, "Circuit breaker open for Azure");
                }
                return azureValidator.validate(accountId, credentialId, credentialValue);
            }
            case GCP -> {
                if (circuitBreakerService.isGcpCircuitOpen()) {
                    logger.warn("GCP circuit breaker is open, returning CIRCUIT_OPEN for {}", credentialId);
                    return VerificationResult.failed(accountId, credentialId, "Circuit breaker open for GCP");
                }
                return gcpValidator.validate(accountId, credentialId, credentialValue);
            }
            default -> {
                return VerificationResult.unknownAccount(credentialId);
            }
        }
    }

    private String extractProviderName(Map<String, Object> rawPayload) {
        if (rawPayload == null) return null;
        Object provider = rawPayload.get("provider");
        if (provider instanceof String) {
            return (String) provider;
        }
        return null;
    }

    private String buildCacheKey(String accountHint, String credentialValue) {
        String hintPart = accountHint != null ? accountHint : "no-hint";
        String valueHash = credentialValue != null ? String.valueOf(credentialValue.hashCode()) : "null";
        return hintPart + ":" + valueHash;
    }
}
