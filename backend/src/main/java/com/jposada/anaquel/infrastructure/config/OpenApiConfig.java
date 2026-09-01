// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    /** URL explicita para que docs/openapi.json sea identico desde el contenedor o desde las pruebas. */
    @Value("${app.api.public-url:http://localhost:8080}")
    private String publicUrl;

    @Bean
    public OpenAPI bibliotecaOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(publicUrl).description("Servidor de la API")))
                .info(new Info()
                        .title("API Biblioteca")
                        .version("1.0.0")
                        .description(
                                "## Sistema de prestamos de una biblioteca\n\n"
                                + "Reemplaza la planilla de Excel: catalogo consultable, prestamos con reglas de "
                                + "atraso y bloqueo, lista de espera, correos automaticos y autocompletado de "
                                + "libros desde Open Library con solo el ISBN.\n\n"
                                + "### Como probar esta API en 4 pasos\n\n"
                                + "1. Abre **Autenticacion -> POST /api/auth/login** y pulsa *Try it out*.\n"
                                + "2. Envia `{\"email\": \"admin@anaquel.app\", \"password\": \"Admin123*\"}` "
                                + "y copia el valor de `token` de la respuesta.\n"
                                + "3. Pulsa el boton verde **Authorize** (arriba a la derecha) y pega el token. "
                                + "Ya no hace falta escribir `Bearer`.\n"
                                + "4. Ahora puedes ejecutar cualquier endpoint. Prueba **GET /api/books**.\n\n"
                                + "### Los dos roles\n\n"
                                + "- **BIBLIOTECARIO**: ve el catalogo, pide prestamos, devuelve y reserva.\n"
                                + "- **ADMIN**: ademas registra y elimina libros, ve las estadisticas y "
                                + "levanta bloqueos de cuentas.\n\n"
                                + "Para ver la diferencia, entra con `lectura@anaquel.app / Admin123*` "
                                + "e intenta un endpoint de ADMIN: recibiras un 403 con el mismo formato de error.\n\n"
                                + "### Las tres reglas de negocio\n\n"
                                + "1. Al prestar: el libro debe estar DISPONIBLE y la cuenta no bloqueada. "
                                + "Se fija la fecha limite a 14 dias y sale el correo de confirmacion.\n"
                                + "2. Al devolver tarde 3 veces en 90 dias: la cuenta se bloquea una semana "
                                + "y se avisa por correo. Un ADMIN puede levantarlo antes.\n"
                                + "3. Al devolver un libro que alguien esperaba: NO vuelve a DISPONIBLE, "
                                + "queda RESERVADO para el primero de la fila, que recibe un correo.\n\n"
                                + "### Errores\n\n"
                                + "Todos tienen la misma forma (`timestamp`, `status`, `code`, `message`, `path`). "
                                + "El campo `code` es estable: es el que debe leer un cliente, no el texto del mensaje.\n\n"
                                + "### Los correos\n\n"
                                + "Se envian de verdad, fuera del hilo de la peticion. "
                                + "Miralos en MailHog: http://localhost:8025")
                        .contact(new Contact().name("Prueba tecnica Ezertech")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
