// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.jposada.anaquel.domain.loan.Loan;

import java.time.LocalDate;

@Schema(name = "LoanResponse", description = "Un prestamo. Los campos overdue, daysOverdue y returnedLate NO son columnas: se calculan al construir la respuesta para que el frontend no repita la logica.")
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        String bookIsbn,
        String bookCoverUrl,
        String borrowerName,
        String borrowerEmail,
        @Schema(description = "Fecha en que se presto", example = "2026-08-18")
        LocalDate loanDate,

        @Schema(description = "Fecha limite: loanDate + 14 dias", example = "2026-09-01")
        LocalDate dueDate,

        @Schema(description = "null mientras no se haya devuelto", example = "2026-08-30")
        LocalDate returnDate,

        @Schema(description = "true si ya se devolvio")
        boolean returned,

        @Schema(description = "Calculado: sin devolver y ya paso la fecha limite")
        boolean overdue,

        @Schema(description = "Calculado: dias transcurridos desde la fecha limite", example = "3")
        long daysOverdue,

        @Schema(description = "Calculado: se devolvio tarde. Cuenta como atraso para la regla de los 3")
        boolean returnedLate
) {
    public static LoanResponse from(Loan loan) {
        LocalDate today = LocalDate.now();
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBook().getAuthor(),
                loan.getBook().getIsbn(),
                loan.getBook().getCoverUrl(),
                loan.getBorrowerName(),
                loan.getBorrowerEmail(),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                loan.isReturned(),
                loan.isOverdue(today),
                loan.daysOverdue(today),
                loan.isReturnedLate());
    }
}
