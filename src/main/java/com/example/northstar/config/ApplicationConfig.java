package com.example.northstar.config;

import java.util.Map;

public final class ApplicationConfig {

    // Credentials are now securely retrieved from Vault

    private static volatile Settings settings;
    private static volatile VaultClient vaultClient;

    private ApplicationConfig() {
    }

    public static void initialize() {
        settings();
    }

    public static String mongodbUri() {
        return settings().mongodbUri();
    }

    public static String mongodbDatabase() {
        return settings().mongodbDatabase();
    }

    public static String jwtSigningKey() {
        return settings().jwtSigningKey();
    }

    public static String loginUsername() {
        return settings().loginUsername();
    }

    public static String loginPassword() {
        return settings().loginPassword();
    }

    public static String smtpHost() {
        return settings().smtpHost();
    }

    public static int smtpPort() {
        return settings().smtpPort();
    }

    public static String smtpFromAddress() {
        return settings().smtpFromAddress();
    }

    public static synchronized void close() {
        settings = null;
        if (vaultClient != null) {
            vaultClient.close();
            vaultClient = null;
        }
    }

    static synchronized void setSettingsForTesting(Map<String, String> values) {
        settings = Settings.from(values);
    }

    private static Settings settings() {
        Settings result = settings;
        if (result == null) {
            synchronized (ApplicationConfig.class) {
                result = settings;
                if (result == null) {
                    VaultClient client = VaultClient.login();
                    try {
                        result = Settings.from(client.readApplicationSecrets());
                        vaultClient = client;
                        settings = result;
                    } catch (RuntimeException exception) {
                        client.close();
                        throw exception;
                    }
                }
            }
        }
        return result;
    }

    record Settings(
            String mongodbUri,
            String mongodbDatabase,
            String jwtSigningKey,
            String loginUsername,
            String loginPassword,
            String smtpHost,
            int smtpPort,
            String smtpFromAddress
    ) {
        static Settings from(Map<String, String> values) {
            return new Settings(
                    required(values, "mongodb_uri"),
                    required(values, "mongodb_database"),
                    required(values, "jwt_signing_key"),
                    required(values, "login_username"),
                    required(values, "login_password"),
                    required(values, "smtp_host"),
                    port(values),
                    required(values, "smtp_from_address")
            );
        }

        private static int port(Map<String, String> values) {
            String value = required(values, "smtp_port");
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Vault secret 'smtp_port' must be a number", exception);
            }
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required Vault secret: " + key);
            }
            return value;
        }
    }
}
