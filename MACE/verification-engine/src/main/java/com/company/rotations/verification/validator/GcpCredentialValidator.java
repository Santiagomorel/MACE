package com.company.rotations.verification.validator;

import com.company.rotations.verification.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GcpCredentialValidator {

    private static final Logger logger = LoggerFactory.getLogger(GcpCredentialValidator.class);

    public VerificationResult validate(String accountId, String credentialId, String credentialValue) {
        logger.info("GCP credential validation is deferred to a future release. credentialId={}, accountId={}",
                credentialId, accountId);

        return VerificationResult.failed(accountId, credentialId,
                "GCP verification is deferred - not in scope for Release 1");
    }
}
