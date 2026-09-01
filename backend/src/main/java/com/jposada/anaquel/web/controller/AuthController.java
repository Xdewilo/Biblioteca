// by Jeremy Posada
package com.jposada.anaquel.web.controller;

import com.jposada.anaquel.infrastructure.security.CurrentUser;
import com.jposada.anaquel.application.account.CrearCuenta;
import com.jposada.anaquel.application.account.CrearCuentaHandler;
import com.jposada.anaquel.application.account.IniciarSesion;
import com.jposada.anaquel.application.account.IniciarSesionHandler;
import com.jposada.anaquel.web.dto.AuthResponse;
import com.jposada.anaquel.web.dto.LoginRequest;
import com.jposada.anaquel.web.dto.RegisterRequest;
import com.jposada.anaquel.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.jposada.anaquel.web.dto.ApiError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Registro y login con JWT")
public class AuthController {

    private final CrearCuentaHandler crearCuenta;
    private final IniciarSesionHandler iniciarSesion;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Crea una cuenta y devuelve el token")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada. Devuelve el token ya listo para usar",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos invalidos: correo mal formado o contrasena de menos de 8 caracteres",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_USED - ese correo ya tiene cuenta",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        var comando = new CrearCuenta(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(crearCuenta.handle(comando));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica y devuelve el token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credenciales correctas. Copia el token en el boton Authorize de arriba",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Falta el correo o la contrasena",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "BAD_CREDENTIALS - misma respuesta si el correo no existe o si la clave es incorrecta, para no revelar quien tiene cuenta",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return iniciarSesion.handle(new IniciarSesion(request.email(), request.password()));
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos del token, incluido si la cuenta esta bloqueada por atrasos",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Falta el token o esta vencido",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserResponse me() {
        return UserResponse.from(CurrentUser.require());
    }
}
