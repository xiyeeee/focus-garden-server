package com.xiye.garden.auth;

import com.xiye.garden.auth.entity.VerificationPurpose;
import com.xiye.garden.auth.service.*;
import com.xiye.garden.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordAndCodeTests {
    private final VerificationProtector protector = new VerificationProtector("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void passwordsPreserveUnicodeSpacesAndBeyond72Bytes() {
        var encoder = new SecurityConfig().passwordEncoder();
        String password = " " + "花".repeat(80) + " ";
        String hash = encoder.encode(password);
        assertTrue(encoder.matches(password, hash));
        assertFalse(encoder.matches(password.strip(), hash));
        assertFalse(encoder.matches(password.substring(0, 40), hash));
    }

    @Test
    void verificationHashBindsPurposeAndEmail() {
        String hash = protector.codeHash("user@example.com", VerificationPurpose.REGISTER, "123456");
        assertTrue(protector.matches(hash, protector.codeHash("user@example.com", VerificationPurpose.REGISTER, "123456")));
        assertFalse(protector.matches(hash, protector.codeHash("other@example.com", VerificationPurpose.REGISTER, "123456")));
        assertFalse(protector.matches(hash, protector.codeHash("user@example.com", VerificationPurpose.RESET_PASSWORD, "123456")));
        assertTrue(protector.generate().matches("[0-9]{6}"));
        assertEquals("user@example.com", AccountIdentifier.normalize(" User@Example.COM "));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new VerificationProtector("c2hvcnQ="));
    }
}
