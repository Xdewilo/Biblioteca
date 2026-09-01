// by Jeremy Posada
package com.jposada.anaquel.domain.reservation;

import com.jposada.anaquel.domain.book.Book;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "requester_email", nullable = false, length = 200)
    private String requesterEmail;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDIENTE;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    /** Momento en que vence el turno de quien fue notificado. null si aun no le toca. */
    public Instant holdExpiresAt(int holdHours) {
        return notifiedAt == null ? null : notifiedAt.plus(java.time.Duration.ofHours(holdHours));
    }

    /** true si le tocaba el turno y dejo pasar el plazo sin confirmar el prestamo. */
    public boolean isHoldExpired(int holdHours) {
        Instant vence = holdExpiresAt(holdHours);
        return status == ReservationStatus.NOTIFICADO && vence != null && vence.isBefore(Instant.now());
    }

    public boolean isWaiting() {
        return status == ReservationStatus.PENDIENTE || status == ReservationStatus.NOTIFICADO;
    }

    @PrePersist
    void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = Instant.now();
        }
    }
}
