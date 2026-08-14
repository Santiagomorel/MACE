package com.company.rotations.actionexecutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VaultServiceImpl implements VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultServiceImpl.class);
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final Map<String, String> credentialsStore;
    private final SecretKey encryptionKey;

    public VaultServiceImpl(
            @Value("${actionexecutor.vault.encryption-key:defaultkey1234567890}") String encryptionKey) {
        this.credentialsStore = new ConcurrentHashMap<>();
        this.encryptionKey = generateKeyFromPassword(encryptionKey);
        log.info("VaultService initialized with encryption key loaded");
    }

    @Override
    public Map<String, String> getAdminCredentials(String tenantId) {
        String encryptedCredentials = credentialsStore.get(buildCredentialKey(tenantId));
        if (encryptedCredentials == null) {
            log.warn("No admin credentials found for tenant {}", tenantId);
            return Map.of();
        }
        try {
            String decrypted = decrypt(encryptedCredentials);
            String[] parts = decrypted.split(":", 2);
            if (parts.length == 2) {
                return Map.of(
                        "accessKeyId", parts[0],
                        "secretAccessKey", parts[1]
                );
            }
            log.error("Invalid credential format for tenant {}", tenantId);
            return Map.of();
        } catch (Exception e) {
            log.error("Failed to decrypt credentials for tenant {}: {}", tenantId, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public void storeNewCredentials(String tenantId, String keyId, String secretAccessKey,
                                    String accessKeyId) {
        storeNewCredentials(tenantId, keyId, secretAccessKey, accessKeyId, null);
    }

    @Override
    public void storeNewCredentials(String tenantId, String keyId, String secretAccessKey,
                                    String accessKeyId, String providerArn) {
        String encrypted = encrypt(accessKeyId + ":" + secretAccessKey);
        String storeKey = buildCredentialKey(tenantId);
        credentialsStore.put(storeKey, encrypted);

        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put("keyId", keyId);
        metadata.put("accessKeyId", encrypt(accessKeyId));
        if (providerArn != null) {
            metadata.put("providerArn", providerArn);
        }
        credentialsStore.put(buildMetadataKey(tenantId), encrypt(formatAsJson(metadata)));

        log.info("Stored new credentials for tenant {} (keyId: {})", tenantId, keyId);
    }

    private String buildCredentialKey(String tenantId) {
        return "creds:" + tenantId;
    }

    private String buildMetadataKey(String tenantId) {
        return "meta:" + tenantId;
    }

    private SecretKey generateKeyFromPassword(String password) {
        try {
            byte[] keyBytes = password.getBytes(StandardCharsets.UTF_8);
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey originalKey = keyGen.generateKey();

            byte[] hash = new byte[32];
            int offset = 0;
            for (int i = 0; i < keyBytes.length && offset < 32; i++) {
                hash[offset++] = keyBytes[i % keyBytes.length];
            }
            int idx = 0;
            while (offset < 32) {
                hash[offset++] = (byte) (idx + offset);
                idx++;
            }
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);

            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            ByteBuffer bb = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            bb.get(iv);

            byte[] ciphertext = new byte[bb.remaining()];
            bb.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private String formatAsJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
