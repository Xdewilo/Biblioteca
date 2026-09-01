// by Jeremy Posada
package com.jposada.anaquel.infrastructure.security;

import com.jposada.anaquel.domain.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos 32 bytes. Definelo en la variable de entorno JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.expirationMinutes()));
        return Jwts.builder()
                .subject(user.getEmail())
                .issuer(properties.issuer())
                .claim("uid", user.getId())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long expirationSeconds() {
        return Duration.ofMinutes(properties.expirationMinutes()).toSeconds();
    }

    /** Devuelve los claims si el token es valido; Optional.empty() si esta vencido o alterado. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
