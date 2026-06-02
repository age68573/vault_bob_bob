package com.example.northstar.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VaultClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(VaultClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BUNDLED_CA_RESOURCE = "/vault-ca.crt";

    private final HttpClient httpClient;
    private final String address;
    private final String namespace;
    private final String token;
    private final String mount;
    private final String secretPath;

    private VaultClient(HttpClient httpClient, String address, String namespace, String token, String mount,
                        String secretPath) {
        this.httpClient = httpClient;
        this.address = address;
        this.namespace = namespace;
        this.token = token;
        this.mount = mount;
        this.secretPath = secretPath;
    }

    static VaultClient login() {
        HttpClient httpClient = httpClient();
        String address = setting("northstar.vault.address", "NORTHSTAR_VAULT_ADDRESS", null);
        String namespace = setting("northstar.vault.namespace", "NORTHSTAR_VAULT_NAMESPACE", "");
        String roleId = credential("northstar.vault.role-id", "NORTHSTAR_VAULT_ROLE_ID",
                "northstar.vault.role-id-file", "NORTHSTAR_VAULT_ROLE_ID_FILE");
        String secretId = credential("northstar.vault.secret-id", "NORTHSTAR_VAULT_SECRET_ID",
                "northstar.vault.secret-id-file", "NORTHSTAR_VAULT_SECRET_ID_FILE");
        String mount = setting("northstar.vault.mount", "NORTHSTAR_VAULT_MOUNT", "secret");
        String secretPath = setting("northstar.vault.secret-path", "NORTHSTAR_VAULT_SECRET_PATH",
                "northstar-customer-center/config");

        String payload = Json.createObjectBuilder()
                .add("role_id", roleId)
                .add("secret_id", secretId)
                .build()
                .toString();
        HttpRequest request = request(address, namespace, "/v1/auth/approle/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        JsonObject response = sendJson(httpClient, request, "AppRole login");
        String token = response.getJsonObject("auth").getString("client_token");
        return new VaultClient(httpClient, address, namespace, token, mount, secretPath);
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .sslContext(bundledCaSslContext())
                .build();
    }

    static SSLContext bundledCaSslContext() {
        try (InputStream input = VaultClient.class.getResourceAsStream(BUNDLED_CA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled Vault CA certificate: " + BUNDLED_CA_RESOURCE);
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(input);
            if (certificates.isEmpty()) {
                throw new IllegalStateException("Bundled Vault CA certificate is empty: " + BUNDLED_CA_RESOURCE);
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trustStore.setCertificateEntry("vault-ca-" + index++, certificate);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load bundled Vault CA certificate", exception);
        }
    }

    Map<String, String> readApplicationSecrets() {
        HttpRequest request = authenticatedRequest("/v1/" + mount + "/data/" + secretPath).GET().build();
        HttpResponse<String> response = send(httpClient, request, "read KV v2 secrets");
        if (response.statusCode() == 404) {
            request = authenticatedRequest("/v1/" + mount + "/" + secretPath).GET().build();
            response = send(httpClient, request, "read KV v1 secrets");
        }
        requireSuccess(response, "read Vault secrets");

        JsonObject data = parse(response.body()).getJsonObject("data");
        if (data.containsKey("data") && data.get("data").getValueType() == JsonValue.ValueType.OBJECT) {
            data = data.getJsonObject("data");
        }
        Map<String, String> secrets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : data.entrySet()) {
            if (entry.getValue().getValueType() != JsonValue.ValueType.STRING) {
                throw new IllegalStateException("Vault secret must be a string: " + entry.getKey());
            }
            secrets.put(entry.getKey(), ((JsonString) entry.getValue()).getString());
        }
        return secrets;
    }

    @Override
    public void close() {
        try {
            HttpRequest request = authenticatedRequest("/v1/auth/token/revoke-self")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = send(httpClient, request, "revoke Vault token");
            requireSuccess(response, "revoke Vault token");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unable to revoke Vault token during application shutdown", exception);
        }
    }

    private HttpRequest.Builder authenticatedRequest(String path) {
        return request(address, namespace, path).header("X-Vault-Token", token);
    }

    private static HttpRequest.Builder request(String address, String namespace, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(trimTrailingSlash(address) + path))
                .timeout(TIMEOUT);
        if (!namespace.isBlank()) {
            builder.header("X-Vault-Namespace", namespace);
        }
        return builder;
    }

    private static JsonObject sendJson(HttpClient client, HttpRequest request, String action) {
        HttpResponse<String> response = send(client, request, action);
        requireSuccess(response, action);
        return parse(response.body());
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request, String action) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to " + action, exception);
        }
    }

    private static void requireSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Unable to " + action + ": Vault returned HTTP " + response.statusCode());
        }
    }

    private static JsonObject parse(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Vault returned an invalid JSON response", exception);
        }
    }

    private static String credential(String propertyName, String environmentName, String filePropertyName,
                                     String fileEnvironmentName) {
        String value = optionalSetting(propertyName, environmentName);
        if (value != null) return value;

        String file = optionalSetting(filePropertyName, fileEnvironmentName);
        if (file == null) {
            throw new IllegalStateException("Missing credential configuration: set " + propertyName + " or "
                    + environmentName + "; alternatively set " + filePropertyName + " or " + fileEnvironmentName);
        }
        try {
            value = Files.readString(Path.of(file)).trim();
            if (value.isBlank()) throw new IllegalStateException("Credential file is empty: " + file);
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read credential file: " + file, exception);
        }
    }

    private static String setting(String propertyName, String environmentName, String defaultValue) {
        String value = optionalSetting(propertyName, environmentName);
        if (value == null || value.isBlank()) value = defaultValue;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration: " + propertyName + " or " + environmentName);
        }
        return value;
    }

    private static String optionalSetting(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) value = System.getenv(environmentName);
        return value == null || value.isBlank() ? null : value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
