// by Jeremy Posada
package com.jposada.anaquel.web.controller;

import com.jposada.anaquel.application.admin.LevantarBloqueo;
import com.jposada.anaquel.application.admin.LevantarBloqueoHandler;
import com.jposada.anaquel.application.admin.query.ConsultarCuentas;
import com.jposada.anaquel.application.admin.query.ConsultarCuentasHandler;
import com.jposada.anaquel.application.admin.query.Estadisticas;
import com.jposada.anaquel.application.admin.query.EstadisticasHandler;
import com.jposada.anaquel.web.dto.StatsResponse;
import com.jposada.anaquel.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.jposada.anaquel.web.dto.ApiError;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administracion", description = "Estadisticas y gestion de cuentas bloqueadas (solo ADMIN)")
public class AdminController {

    private final EstadisticasHandler estadisticas;
    private final ConsultarCuentasHandler consultarCuentas;
    private final LevantarBloqueoHandler levantarBloqueo;

    @GetMapping("/stats")
    @Operation(summary = "Estadisticas globales: prestados, vencidos, bloqueados")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contadores del catalogo, los prestamos y las cuentas bloqueadas",
                content = @Content(schema = @Schema(implementation = StatsResponse.class))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public StatsResponse stats() {
        return estadisticas.handle(new Estadisticas());
    }

    @GetMapping("/users")
    @Operation(summary = "Todas las cuentas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todas las cuentas"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<UserResponse> users() {
        return consultarCuentas.handle(new ConsultarCuentas(false));
    }

    @GetMapping("/users/blocked")
    @Operation(summary = "Cuentas bloqueadas ahora mismo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuentas con el bloqueo vigente ahora mismo"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<UserResponse> blockedUsers() {
        return consultarCuentas.handle(new ConsultarCuentas(true));
    }

    @PostMapping("/users/{id}/unblock")
    @Operation(summary = "Levanta el bloqueo de una cuenta antes de que se cumpla el plazo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bloqueo levantado: la cuenta ya puede pedir prestamos",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un usuario con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserResponse unblock(@PathVariable Long id) {
        return levantarBloqueo.handle(new LevantarBloqueo(id));
    }
}
