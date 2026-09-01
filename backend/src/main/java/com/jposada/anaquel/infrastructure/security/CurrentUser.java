// by Jeremy Posada
package com.jposada.anaquel.infrastructure.security;

import com.jposada.anaquel.domain.user.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AppUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal.user());
    }

    public static AppUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No hay usuario autenticado en el contexto"));
    }
}
