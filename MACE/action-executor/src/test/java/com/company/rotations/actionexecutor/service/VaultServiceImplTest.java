package com.company.rotations.actionexecutor.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultServiceImplTest {

    private static final String ENCRYPTION_KEY = "testencryptionkey123456789012";

    @Test
    void vaultServiceInitializesWithEncryptionKey() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        assertNotNull(vault);
    }

    @Test
    void getAdminCredentials_whenNoCredentialsStored() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        Map<String, String> result = vault.getAdminCredentials("nonexistent-tenant");
        assertTrue(result.isEmpty());
    }

    @Test
    void storeAndRetrieveAdminCredentials() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String tenantId = "test-tenant";

        vault.storeNewCredentials(tenantId, "AKIANEW123", "secret123", "AKIAEXISTING456");

        Map<String, String> creds = vault.getAdminCredentials(tenantId);
        assertFalse(creds.isEmpty());
        assertEquals("AKIAEXISTING456", creds.get("accessKeyId"));
        assertEquals("secret123", creds.get("secretAccessKey"));
    }

    @Test
    void storeCredentialsWithProviderArn() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String tenantId = "test-tenant";
        String providerArn = "arn:aws:iam::123456789012:user/testuser";

        vault.storeNewCredentials(tenantId, "AKIANEW123", "secret123", "AKIAEXISTING456", providerArn);

        // No exception means success; verify we can retrieve
        Map<String, String> creds = vault.getAdminCredentials(tenantId);
        assertFalse(creds.isEmpty());
        assertEquals("AKIAEXISTING456", creds.get("accessKeyId"));
    }

    @Test
    void storeCredentialsWithoutProviderArn() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String tenantId = "test-tenant-2";

        vault.storeNewCredentials(tenantId, "AKIANEW456", "secret456", "AKIAEXISTING789", null);

        Map<String, String> creds = vault.getAdminCredentials(tenantId);
        assertFalse(creds.isEmpty());
        assertEquals("AKIAEXISTING789", creds.get("accessKeyId"));
    }

    @Test
    void storeCredentialsOverwritesPrevious() {
        VaultService vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String tenantId = "test-tenant";

        vault.storeNewCredentials(tenantId, "AKIAKEY1", "secret1", "AKIAEXISTING1");
        Map<String, String> first = vault.getAdminCredentials(tenantId);
        assertEquals("AKIAEXISTING1", first.get("accessKeyId"));

        vault.storeNewCredentials(tenantId, "AKIAKEY2", "secret2", "AKIAEXISTING2");
        Map<String, String> second = vault.getAdminCredentials(tenantId);
        assertEquals("AKIAEXISTING2", second.get("accessKeyId"));
        assertEquals("secret2", second.get("secretAccessKey"));
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String plaintext = "accesskey:secretkey";

        String encrypted = vault.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertTrue(encrypted.length() > plaintext.length());

        String decrypted = vault.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_differentResultsForSameInput() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String plaintext = "same-input";

        String encrypted1 = vault.encrypt(plaintext);
        String encrypted2 = vault.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void decrypt_invalidBase64_throwsException() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        assertThrows(RuntimeException.class, () -> vault.decrypt("not-valid-base64!!"));
    }

    @Test
    void decrypt_wrongKey_throwsException() {
        VaultServiceImpl vault1 = new VaultServiceImpl("key1234567890123456789012345678901");
        String encrypted = vault1.encrypt("test-value");

        VaultServiceImpl vault2 = new VaultServiceImpl("differentkey12345678901234567890");
        assertThrows(RuntimeException.class, () -> vault2.decrypt(encrypted));
    }

    @Test
    void encrypt_decryptWithDifferentKeys() {
        VaultServiceImpl vault1 = new VaultServiceImpl("key1111111111111111111111111111111");
        String encrypted = vault1.encrypt("sensitive-data");

        VaultServiceImpl vault2 = new VaultServiceImpl("key2222222222222222222222222222222");
        assertThrows(RuntimeException.class, () -> vault2.decrypt(encrypted));
    }

    @Test
    void encrypt_largeValue() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String largeValue = "x".repeat(10000);

        String encrypted = vault.encrypt(largeValue);
        String decrypted = vault.decrypt(encrypted);
        assertEquals(largeValue, decrypted);
    }

    @Test
    void encrypt_emptyValue() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String encrypted = vault.encrypt("");
        String decrypted = vault.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void encrypt_specialCharacters() {
        VaultServiceImpl vault = new VaultServiceImpl(ENCRYPTION_KEY);
        String specialValue = "key:pass\\word\"with\"quotes&special";

        String encrypted = vault.encrypt(specialValue);
        String decrypted = vault.decrypt(encrypted);
        assertEquals(specialValue, decrypted);
    }
}
