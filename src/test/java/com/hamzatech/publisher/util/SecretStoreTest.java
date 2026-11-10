package com.hamzatech.publisher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SecretStoreTest {

    @Test
    void protectThenReveal_roundTripsExactly() {
        assumeTrue(SecretStore.isEncryptionAvailable(), "DPAPI is Windows-only");

        String secret = "sk-proj-AbCdEf1234567890";
        String stored = SecretStore.protect(secret);

        assertNotEquals(secret, stored, "the stored form must not be the plain value");
        assertFalse(stored.contains(secret), "the plain value must not appear inside the stored form");
        assertTrue(SecretStore.isProtected(stored));
        assertEquals(secret, SecretStore.reveal(stored));
    }

    @Test
    void roundTrip_survivesNonAsciiAndSpaces() {
        assumeTrue(SecretStore.isEncryptionAvailable(), "DPAPI is Windows-only");

        // كلمات مرور تطبيق ووردبريس تأتي بمسافات، والأسماء قد تكون عربية
        String secret = "abcd EFGH ijkl MNOP — مفتاح";
        assertEquals(secret, SecretStore.reveal(SecretStore.protect(secret)));
    }

    @Test
    void reveal_passesThroughLegacyPlainText() {
        // هذا ما يجعل الترحيل تلقائيًا: ملف قديم بقيم صريحة يُقرأ كما هو
        assertEquals("plain-old-key", SecretStore.reveal("plain-old-key"));
        assertFalse(SecretStore.isProtected("plain-old-key"));
    }

    @Test
    void reveal_returnsNullWhenTheStoredValueCannotBeDecrypted() {
        assumeTrue(SecretStore.isEncryptionAvailable(), "DPAPI is Windows-only");

        // ما يحدث فعليًا حين يُنقل الملف من جهاز أو حساب مستخدم آخر
        assertNull(SecretStore.reveal("dpapi:bm90LXJlYWxseS1lbmNyeXB0ZWQ="));
        assertNull(SecretStore.reveal("dpapi:!!!not-base64!!!"));
    }

    @Test
    void protect_leavesEmptyValuesAlone() {
        assertNull(SecretStore.protect(null));
        assertEquals("", SecretStore.protect(""));
        assertNull(SecretStore.reveal(null));
    }

    @Test
    void protect_producesADifferentBlobEachTime() {
        assumeTrue(SecretStore.isEncryptionAvailable(), "DPAPI is Windows-only");

        String secret = "same-secret";
        assertNotEquals(SecretStore.protect(secret), SecretStore.protect(secret),
                "DPAPI salts each call, so identical secrets must not produce identical files");
    }
}
