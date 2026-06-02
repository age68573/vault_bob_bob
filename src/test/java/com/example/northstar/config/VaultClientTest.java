package com.example.northstar.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class VaultClientTest {

    @Test
    void bundledVaultCaCanCreateSslContext() {
        assertNotNull(VaultClient.bundledCaSslContext());
    }
}
