package com.company.rotations.actionexecutor.service;

import java.util.Map;
import java.util.UUID;

public interface VaultService {

    Map<String, String> getAdminCredentials(String tenantId);

    void storeNewCredentials(String tenantId, String keyId, String secretAccessKey,
                             String accessKeyId);

    void storeNewCredentials(String tenantId, String keyId, String secretAccessKey,
                             String accessKeyId, String providerArn);
}
