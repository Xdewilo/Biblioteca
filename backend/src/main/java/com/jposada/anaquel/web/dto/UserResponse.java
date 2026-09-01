// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean blocked,
        Instant blockedUntil,
        String blockedReason
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.isBlocked(), user.getBlockedUntil(), user.getBlockedReason());
    }
}
