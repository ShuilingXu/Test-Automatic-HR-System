package com.autohr.common.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3EndpointValidatorTest {

    @Test
    void requiresHttpsByDefault() {
        assertFalse(S3EndpointValidator.isAllowed("http://1.1.1.1:9000", false, false));
        assertTrue(S3EndpointValidator.isAllowed("http://1.1.1.1:9000", true, false));
    }

    @Test
    void requiresExplicitPrivateAddressOptIn() {
        assertFalse(S3EndpointValidator.isAllowed("https://127.0.0.1:9000", false, false));
        assertFalse(S3EndpointValidator.isAllowed("https://10.0.0.8:9000", false, false));
        assertTrue(S3EndpointValidator.isAllowed("https://10.0.0.8:9000", false, true));
    }

    @Test
    void rejectsEmbeddedCredentialsEvenWhenCompatibilitySwitchesAreEnabled() {
        assertFalse(S3EndpointValidator.isAllowed("http://user:secret@127.0.0.1:9000", true, true));
    }
}
