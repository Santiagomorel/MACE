package com.company.rotations.alerting.dto;

import com.company.rotations.alerting.validation.ValidEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertRequest(
    @NotBlank(message = "providerName is required")
    String providerName,

    @ValidEnum(enumClass = "com.company.rotations.models.AlertType", ignoreCase = "false",
        message = "credentialType must be one of: AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL, GENERIC")
    String credentialType,

    @NotBlank(message = "tenantId is required")
    String tenantId,

    String repository,
    String rawPayload
) {
}
