// by Jeremy Posada
package com.jposada.anaquel.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** El secreto llega por JWT_SECRET; nunca se versiona. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMinutes,
        String issuer
) {
    public JwtProperties {
        if (expirationMinutes <= 0) {
            expirationMinutes = 480;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "anaquel-api";
        }
    }
}
