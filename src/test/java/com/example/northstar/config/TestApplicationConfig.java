package com.example.northstar.config;

import java.util.Map;

public final class TestApplicationConfig {

    private TestApplicationConfig() {
    }

    public static void install() {
        ApplicationConfig.setSettingsForTesting(Map.of(
                "mongodb_uri", "mongodb://test:test@127.0.0.1:27017",
                "mongodb_database", "test",
                "jwt_signing_key", "test-signing-key",
                "login_username", "operator",
                "login_password", "test-password",
                "smtp_host", "127.0.0.1",
                "smtp_port", "25",
                "smtp_from_address", "test@example.com"
        ));
    }

    public static void reset() {
        ApplicationConfig.close();
    }
}
