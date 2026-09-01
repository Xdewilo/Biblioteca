// by Jeremy Posada
package com.jposada.anaquel.web.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {}
