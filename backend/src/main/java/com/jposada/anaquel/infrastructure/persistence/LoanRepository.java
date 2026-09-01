// by Jeremy Posada
package com.jposada.anaquel.infrastructure.persistence;

import com.jposada.anaquel.domain.loan.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Query("""
            select l from Loan l join fetch l.book
            where lower(l.borrowerEmail) = lower(:email)
            order by l.returnDate nulls first, l.dueDate asc
            """)
    List<Loan> findByBorrowerEmail(String email);

    boolean existsByBookIdAndReturnDateIsNull(Long bookId);

    boolean existsByBookIdAndBorrowerEmailIgnoreCaseAndReturnDateIsNull(Long bookId, String borrowerEmail);

    /** Prestamos activos que vencen dentro de la ventana [desde, hasta] y aun no tienen recordatorio. */
    @Query("""
            select l from Loan l join fetch l.book
            where l.returnDate is null
              and l.reminderSentAt is null
              and l.dueDate between :from and :to
            """)
    List<Loan> findDueSoonWithoutReminder(LocalDate from, LocalDate to);

    /** Prestamos ya vencidos, sin devolver, a los que todavia no se les mando el aviso. */
    @Query("""
            select l from Loan l join fetch l.book
            where l.returnDate is null
              and l.overdueNoticeSentAt is null
              and l.dueDate < :reference
            """)
    List<Loan> findOverdueWithoutNotice(LocalDate reference);

    /** Atrasos (devoluciones tardias) de una persona dentro de una ventana de tiempo. */
    @Query("""
            select count(l) from Loan l
            where lower(l.borrowerEmail) = lower(:email)
              and l.returnDate is not null
              and l.returnDate > l.dueDate
              and l.returnDate >= :since
            """)
    long countLateReturnsSince(String email, LocalDate since);

    @Query("select count(l) from Loan l where l.returnDate is null")
    long countActive();

    @Query("select count(l) from Loan l where l.returnDate is null and l.dueDate < :reference")
    long countOverdue(LocalDate reference);

    @Query("select count(l) from Loan l where l.returnDate is null and l.dueDate between :from and :to")
    long countDueSoon(LocalDate from, LocalDate to);
}
