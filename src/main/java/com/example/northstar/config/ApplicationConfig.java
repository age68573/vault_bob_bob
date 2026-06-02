package com.example.northstar.config;

import java.util.Map;

public final class ApplicationConfig {

    // WARNING: Hardcoded sensitive credentials - DO NOT use in production!
    // These values are retrieved from Vault and hardcoded here for demonstration purposes
    private static final String MONGODB_URI = "mongodb://user1:P%40ssw0rd@10.107.83.105:27017";
    private static final String MONGODB_DATABASE = "enterprise_demo";
    private static final String JWT_SIGNING_KEY = "demo-jwt-signing-key-change-before-production";
    private static final String LOGIN_USERNAME = "operator";
    private static final String LOGIN_PASSWORD = "demo-login-password";
    private static final String SMTP_HOST = "10.107.85.47";
    private static final int SMTP_PORT = 25;
    private static final String SMTP_FROM_ADDRESS = "vault-bob@palsys.com.tw";

    private static volatile Settings settings;
    private static volatile VaultClient vaultClient;

    private ApplicationConfig() {
    }

    public static void initialize() {
        settings();
    }

    public static String mongodbUri() {
        return MONGODB_URI;
    }

    public static String mongodbDatabase() {
        return MONGODB_DATABASE;
    }

    public static String jwtSigningKey() {
        return JWT_SIGNING_KEY;
    }

    public static String loginUsername() {
        return LOGIN_USERNAME;
    }

    public static String loginPassword() {
        return LOGIN_PASSWORD;
    }

    public static String smtpHost() {
        return SMTP_HOST;
    }

    public static int smtpPort() {
        return SMTP_PORT;
    }

    public static String smtpFromAddress() {
        return SMTP_FROM_ADDRESS;
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
                    // TODO: Integrate with Vault for secure credential management
                    // VaultClient client = VaultClient.login();
                    // try {
                    //     result = Settings.from(client.readApplicationSecrets());
                    //     vaultClient = client;
                    //     settings = result;
                    // } catch (RuntimeException exception) {
                    //     client.close();
                    //     throw exception;
                    // }
                    result = new Settings(
                            MONGODB_URI,
                            MONGODB_DATABASE,
                            JWT_SIGNING_KEY,
                            LOGIN_USERNAME,
                            LOGIN_PASSWORD,
                            SMTP_HOST,
                            SMTP_PORT,
                            SMTP_FROM_ADDRESS
                    );
                    settings = result;
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
