package com.company.rotations.verification.validator;

import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.model.PermissionMatrix;
import com.company.rotations.verification.model.VerificationResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
import software.amazon.awssdk.services.sts.model.StsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CredentialValidatorService Tests")
@ExtendWith(MockitoExtension.class)
class CredentialValidatorServiceTest {

    @Mock
    private AwsCredentialValidator awsValidator;

    @Mock
    private AzureCredentialValidator azureValidator;

    @Mock
    private GcpCredentialValidator gcpValidator;

    @Mock
    private PermissionEnumerator permissionEnumerator;

    @BeforeEach
    void setUp() {
        when(permissionEnumerator.enumeratePermissions(any(IamClient.class), any(String.class)))
                .thenReturn(new PermissionMatrix());
    }
}
