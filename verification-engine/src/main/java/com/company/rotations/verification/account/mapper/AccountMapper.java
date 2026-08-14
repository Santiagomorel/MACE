package com.company.rotations.verification.account.mapper;

import com.company.rotations.verification.model.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AccountMapper {

    private static final Logger logger = LoggerFactory.getLogger(AccountMapper.class);

    public String mapAccount(String accountHint, String credentialId, List<String> knownAccountIds, ProviderType providerType) {
        if (accountHint != null && !accountHint.isBlank()) {
            logger.debug("Using GitGuardian account_hint for mapping: {}", accountHint);
            return accountHint;
        }

        String iterativeResult = iterativeLookup(credentialId, knownAccountIds, providerType);
        if (iterativeResult != null) {
            logger.debug("Account mapped via iterative lookup: {}", iterativeResult);
            return iterativeResult;
        }

        logger.warn("Account mapping failed for credential {}, marking as UNKNOWN", credentialId);
        return null;
    }

    private String iterativeLookup(String credentialId, List<String> knownAccountIds, ProviderType providerType) {
        if (knownAccountIds == null || knownAccountIds.isEmpty()) {
            return null;
        }

        logger.debug("Starting iterative account lookup for {} known accounts", knownAccountIds.size());

        for (String accountId : knownAccountIds) {
            boolean match = tryValidateCredentialForAccount(credentialId, accountId, providerType);
            if (match) {
                logger.debug("Credential matched account: {}", accountId);
                return accountId;
            }
        }

        return null;
    }

    private boolean tryValidateCredentialForAccount(String credentialId, String accountId, ProviderType providerType) {
        switch (providerType) {
            case AWS -> {
                return tryValidateAwsCredential(credentialId, accountId);
            }
            case AZURE -> {
                logger.debug("Azure account mapping deferred to future implementation");
                return false;
            }
            case GCP -> {
                logger.debug("GCP account mapping deferred to future implementation");
                return false;
            }
            default -> {
                logger.debug("Unknown provider type {}, cannot validate", providerType);
                return false;
            }
        }
    }

    private boolean tryValidateAwsCredential(String credentialId, String accountId) {
        return false;
    }
}
