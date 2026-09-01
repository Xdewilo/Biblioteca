// by Jeremy Posada
package com.jposada.anaquel.web.controller;

import com.jposada.anaquel.application.loan.DevolverPrestamo;
import com.jposada.anaquel.application.loan.DevolverPrestamoHandler;
import com.jposada.anaquel.application.loan.RegistrarPrestamo;
import com.jposada.anaquel.application.loan.RegistrarPrestamoHandler;
import com.jposada.anaquel.application.loan.query.MisPrestamos;
import com.jposada.anaquel.application.loan.query.MisPrestamosHandler;
import com.jposada.anaquel.application.loan.query.TodosLosPrestamos;
import com.jposada.anaquel.application.loan.query.TodosLosPrestamosHandler;
import com.jposada.anaquel.infrastructure.security.CurrentUser;
import com.jposada.anaquel.web.dto.ApiError;
import com.jposada.anaquel.web.dto.CreateLoanRequest;
import com.jposada.anaquel.web.dto.LoanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
@Tag(name = "Prestamos", description = "Registro y devolucion de prestamos")
public class LoanController {

    private final RegistrarPrestamoHandler registrarPrestamo;
    private final DevolverPrestamoHandler devolverPrestamo;
    private final MisPrestamosHandler misPrestamos;
    private final TodosLosPrestamosHandler todosLosPrestamos;

    @PostMapping
    @Operation(summary = "Registra un prestamo. Valida disponibilidad y bloqueo por atrasos")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Prestamo registrado: el libro pasa a PRESTADO, dueDate = hoy + 14 dias y sale el correo de confirmacion",
                content = @Content(schema = @Schema(implementation = LoanResponse.class))),
        @ApiResponse(responseCode = "400", description = "Falta el bookId",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "USER_BLOCKED (cuenta bloqueada por 3 atrasos) o FORBIDDEN_BORROWER (solo un ADMIN presta a nombre de otro)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un libro con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "BOOK_NOT_AVAILABLE - el libro esta PRESTADO, o RESERVADO para otra persona",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request) {
        var comando = new RegistrarPrestamo(
                request.bookId(), request.borrowerName(), request.borrowerEmail(), CurrentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(registrarPrestamo.handle(comando));
    }

    @GetMapping("/mine")
    @Operation(summary = "Prestamos del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tus prestamos, activos e historicos, con el calculo de vencimiento"),
        @ApiResponse(responseCode = "401", description = "Falta el token",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<LoanResponse> mine() {
        return misPrestamos.handle(new MisPrestamos(CurrentUser.require()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Todos los prestamos (ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todos los prestamos del sistema"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<LoanResponse> all() {
        return todosLosPrestamos.handle(new TodosLosPrestamos());
    }

    @PutMapping("/{id}/return")
    @Operation(summary = "Marca el prestamo como devuelto. Aplica atrasos y lista de espera")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Devuelto. Si fue tarde suma un atraso; al tercero en 90 dias la cuenta se bloquea. Si habia lista de espera, el libro queda RESERVADO y se avisa al primero de la fila",
                content = @Content(schema = @Schema(implementation = LoanResponse.class))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN_LOAN - solo puedes devolver tus propios prestamos (salvo ADMIN)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un prestamo con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "LOAN_ALREADY_RETURNED - ese prestamo ya se devolvio",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public LoanResponse markReturned(@PathVariable Long id) {
        return devolverPrestamo.handle(new DevolverPrestamo(id, CurrentUser.require()));
    }
}
