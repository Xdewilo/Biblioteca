// by Jeremy Posada
package com.jposada.anaquel.domain.loan;

import com.jposada.anaquel.domain.book.Book;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    public static final int LOAN_PERIOD_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "borrower_name", nullable = false, length = 200)
    private String borrowerName;

    @Column(name = "borrower_email", nullable = false, length = 200)
    private String borrowerEmail;

    @Column(name = "loan_date", nullable = false)
    private LocalDate loanDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    /** Evita mandar el recordatorio dos veces. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    /** Evita repetir el aviso de vencido. */
    @Column(name = "overdue_notice_sent_at")
    private Instant overdueNoticeSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.loanDate == null) {
            this.loanDate = LocalDate.now();
        }
        if (this.dueDate == null) {
            this.dueDate = this.loanDate.plusDays(LOAN_PERIOD_DAYS);
        }
    }

    public boolean isReturned() {
        return returnDate != null;
    }

    public boolean isOverdue(LocalDate reference) {
        return returnDate == null && reference.isAfter(dueDate);
    }

    /** Se devolvio tarde: cuenta como atraso para la regla de los 3 atrasos. */
    public boolean isReturnedLate() {
        return returnDate != null && returnDate.isAfter(dueDate);
    }

    public long daysOverdue(LocalDate reference) {
        if (returnDate != null || !reference.isAfter(dueDate)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, reference);
    }
}
