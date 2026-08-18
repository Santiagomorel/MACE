package com.company.rotations.verification.repository;

import com.company.rotations.verification.config.CircuitBreakerService;
import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.provider.AwsStsVerificationProvider;
import com.company.rotations.verification.provider.ProviderDetector;
import com.company.rotations.verification.severity.BlastRadiusCalculator;
import com.company.rotations.verification.severity.SeverityRuleEngine;
import com.company.rotations.verification.service.VerificationEngineService;
import com.company.rotations.verification.cache.VerificationCacheService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import io.github.resilience4j.retry.RetryRegistry;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;

@TestConfiguration
@ComponentScan(basePackages = "com.company.rotations", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                VerificationResultRepository.class,
                VerificationResultRepositoryIntegrationTest.class
        })
})
class RepositoryTestConfig {

    @MockBean
    private CircuitBreakerService circuitBreakerService;

    @MockBean
    private SeverityRuleEngine severityRuleEngine;

    @MockBean
    private BlastRadiusCalculator blastRadiusCalculator;

    @MockBean
    private VerificationCacheService verificationCacheService;

    @MockBean
    private ProviderDetector providerDetector;

    @MockBean
    private PermissionEnumerator permissionEnumerator;

    @MockBean
    private AwsStsVerificationProvider awsStsVerificationProvider;

    @MockBean
    private VerificationEngineService verificationEngineService;

    @MockBean
    private IamClient iamClient;

    @MockBean
    private StsClient stsClient;

    @MockBean
    private RetryRegistry retryRegistry;
}
