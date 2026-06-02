package com.example.northstar.security;

import com.example.northstar.config.TestApplicationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @BeforeEach
    void setUp() {
        TestApplicationConfig.install();
    }

    @AfterEach
    void tearDown() {
        TestApplicationConfig.reset();
    }

    @Test
    void issuedTokenCanBeValidated() {
        String token = JwtService.issue("operator");

        JwtService.TokenValidation validation = JwtService.validate(token);

        assertTrue(validation.valid());
    }

    @Test
    void modifiedTokenFailsSignatureValidation() {
        String token = JwtService.issue("operator");
        String modifiedToken = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        JwtService.TokenValidation validation = JwtService.validate(modifiedToken);

        assertFalse(validation.valid());
    }
}
