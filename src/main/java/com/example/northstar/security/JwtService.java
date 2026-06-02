package com.example.northstar.security;

import com.example.northstar.config.ApplicationConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public final class JwtService {

    private static final long TOKEN_VALID_SECONDS = 900;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private JwtService() {
    }

    public static String issue(String subject) {
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_VALID_SECONDS;
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + subject + "\",\"role\":\"operator\",\"exp\":" + expiresAt + "}");
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public static TokenValidation validate(String token) {
        if (token == null || token.isBlank()) {
            return TokenValidation.invalid("尚未登入，請先取得 JWT token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return TokenValidation.invalid("JWT 格式錯誤");
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return TokenValidation.invalid("JWT 簽章驗證失敗");
        }

        try {
            String payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            String subject = jsonString(payload, "sub");
            long expiresAt = jsonLong(payload, "exp");
            if (Instant.now().getEpochSecond() >= expiresAt) {
                return TokenValidation.invalid("JWT 已過期");
            }
            return TokenValidation.valid(subject, expiresAt);
        } catch (RuntimeException exception) {
            return TokenValidation.invalid("JWT payload 無法解析");
        }
    }

    public static long tokenValidSeconds() {
        return TOKEN_VALID_SECONDS;
    }

    private static String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(ApplicationConfig.jwtSigningKey().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(String json, String name) {
        String marker = "\"" + name + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("Missing JSON field");
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        if (end < 0) throw new IllegalArgumentException("Invalid JSON field");
        return json.substring(valueStart, end);
    }

    private static long jsonLong(String json, String name) {
        String marker = "\"" + name + "\":";
        int start = json.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("Missing JSON field");
        int valueStart = start + marker.length();
        int end = json.indexOf(',', valueStart);
        if (end < 0) end = json.indexOf('}', valueStart);
        return Long.parseLong(json.substring(valueStart, end));
    }

    public record TokenValidation(boolean valid, String subject, long expiresAt, String message) {
        static TokenValidation valid(String subject, long expiresAt) {
            return new TokenValidation(true, subject, expiresAt, "JWT 簽章有效");
        }

        static TokenValidation invalid(String message) {
            return new TokenValidation(false, null, 0, message);
        }
    }
}
