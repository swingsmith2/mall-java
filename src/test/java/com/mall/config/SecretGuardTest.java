package com.mall.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretGuardTest {

    @Test
    void rejectsBlankOrPlaceholder() {
        MallProperties blank = new MallProperties();
        assertThrows(IllegalStateException.class, () -> new SecretGuard(blank).verify());

        MallProperties placeholder = new MallProperties();
        placeholder.getJwt().setSecret("change-me-to-a-long-random-secret-at-least-256-bits");
        placeholder.getPayment().setCallbackSecret("test-pay-callback-secret-32-bytes!!");
        assertThrows(IllegalStateException.class, () -> new SecretGuard(placeholder).verify());
    }

    @Test
    void acceptsLongDistinctSecrets() {
        MallProperties props = new MallProperties();
        props.getJwt().setSecret("test-jwt-secret-at-least-32-bytes-long!!");
        props.getPayment().setCallbackSecret("test-pay-callback-secret-32-bytes!!");
        assertDoesNotThrow(() -> new SecretGuard(props).verify());
    }
}
