package com.example.northstar.security;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static JwtService.TokenValidation validate(HttpHeaders headers) {
        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return JwtService.validate(null);
        }
        return JwtService.validate(authorization.substring("Bearer ".length()));
    }

    public static Response unauthorized(JwtService.TokenValidation validation) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", validation.message()))
                .build();
    }
}
