// by Jeremy Posada
package com.jposada.anaquel.infrastructure.persistence;

import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** El primero de la fila: la reserva PENDIENTE mas antigua de ese libro. */
    @Query("""
            select r from Reservation r join fetch r.book
            where r.book.id = :bookId and r.status = com.jposada.anaquel.domain.reservation.ReservationStatus.PENDIENTE
            order by r.requestedAt asc
            """)
    List<Reservation> findQueueForBook(Long bookId);

    default Optional<Reservation> findFirstInQueue(Long bookId) {
        return findQueueForBook(bookId).stream().findFirst();
    }

    boolean existsByBookIdAndRequesterEmailIgnoreCaseAndStatusIn(
            Long bookId, String requesterEmail, List<ReservationStatus> statuses);

    @Query("""
            select r from Reservation r join fetch r.book
            where lower(r.requesterEmail) = lower(:email)
            order by r.requestedAt desc
            """)
    List<Reservation> findByRequesterEmail(String email);

    long countByStatus(ReservationStatus status);

    /** Turnos ya notificados a los que se les paso el plazo sin confirmar el prestamo. */
    @Query("""
            select r from Reservation r join fetch r.book
            where r.status = com.jposada.anaquel.domain.reservation.ReservationStatus.NOTIFICADO
              and r.notifiedAt < :limite
            order by r.notifiedAt asc
            """)
    List<Reservation> findExpiredHolds(java.time.Instant limite);

    Optional<Reservation> findFirstByBookIdAndRequesterEmailIgnoreCaseAndStatusOrderByRequestedAtAsc(
            Long bookId, String requesterEmail, ReservationStatus status);
}
