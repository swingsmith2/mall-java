package com.mall.payment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSignerTest {

    private static final String SECRET = "test-pay-callback-secret-32-bytes!!";

    @Test
    void signIsStableAndRejectsTampering() {
        String sig = PaymentSigner.sign(SECRET, 7L, "pay-1", 12900L, "SUCCESS");
        assertEquals(64, sig.length());
        assertTrue(sig.chars().allMatch(ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')));
        byte[] expected = sig.getBytes(StandardCharsets.UTF_8);
        byte[] same = PaymentSigner.sign(SECRET, 7L, "pay-1", 12900L, "SUCCESS").getBytes(StandardCharsets.UTF_8);
        assertEquals(expected.length, same.length);
        assertTrue(java.security.MessageDigest.isEqual(expected, same));
        assertFalse(sig.equals(PaymentSigner.sign(SECRET, 7L, "pay-1", 12901L, "SUCCESS")));
        assertTrue(SECRET.getBytes(StandardCharsets.UTF_8).length >= 32);
    }
}
