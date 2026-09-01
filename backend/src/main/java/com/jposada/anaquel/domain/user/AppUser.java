// by Jeremy Posada
package com.jposada.anaquel.domain.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    /** Siempre hash BCrypt, nunca texto plano. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.BIBLIOTECARIO;

    /** Hasta cuando no puede pedir prestamos; null o en el pasado = habilitada. */
    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "blocked_reason", length = 300)
    private String blockedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isBlocked() {
        return blockedUntil != null && blockedUntil.isAfter(Instant.now());
    }

    public void block(Instant until, String reason) {
        this.blockedUntil = until;
        this.blockedReason = reason;
    }

    public void unblock() {
        this.blockedUntil = null;
        this.blockedReason = null;
    }
}
