package com.example.northstar.config;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationConfigTest {

    @Test
    void settingsAreCreatedFromVaultSecrets() {
        ApplicationConfig.Settings settings = ApplicationConfig.Settings.from(secrets());
        ConnectionString connectionString = new ConnectionString(settings.mongodbUri());

        assertEquals("user1", connectionString.getCredential().getUserName());
        assertEquals("test@password", new String(connectionString.getCredential().getPassword()));
        assertEquals("10.107.83.105:27017", connectionString.getHosts().get(0));
        assertEquals(25, settings.smtpPort());
    }

    @Test
    void missingRequiredSecretFailsFast() {
        Map<String, String> secrets = secrets();
        secrets.remove("jwt_signing_key");

        assertThrows(IllegalStateException.class, () -> ApplicationConfig.Settings.from(secrets));
    }

    private static Map<String, String> secrets() {
        Map<String, String> values = new HashMap<>();
        values.put("mongodb_uri", "mongodb://user1:test%40password@10.107.83.105:27017");
        values.put("mongodb_database", "enterprise_demo");
        values.put("jwt_signing_key", "test-signing-key");
        values.put("login_username", "operator");
        values.put("login_password", "test-password");
        values.put("smtp_host", "127.0.0.1");
        values.put("smtp_port", "25");
        values.put("smtp_from_address", "test@example.com");
        return values;
    }
}
