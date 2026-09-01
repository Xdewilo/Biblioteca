// by Jeremy Posada
package com.jposada.anaquel.web;

import com.jposada.anaquel.web.dto.ApiError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica el spec OpenAPI y lo exporta a docs/openapi.json en cada mvn test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI - la documentacion esta completa y se exporta a docs/openapi.json")
class OpenApiSpecIT {

    private static final List<String> RUTAS_EXIGIDAS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/books",
            "/api/books/lookup/{isbn}",
            "/api/books/{id}",
            "/api/loans",
            "/api/loans/mine",
            "/api/loans/{id}/return",
            "/api/reservations",
            "/api/reservations/{id}",
            "/api/admin/stats");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("expone todas las rutas exigidas, documenta sus errores y exporta el spec")
    void generatesCompleteSpec() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode spec = objectMapper.readTree(raw);
        JsonNode paths = spec.get("paths");

        // 1. Estan todas las rutas de la prueba
        for (String ruta : RUTAS_EXIGIDAS) {
            assertThat(paths.has(ruta)).as("falta documentar la ruta %s", ruta).isTrue();
        }

        // 2. El esquema de seguridad JWT esta declarado (habilita el boton Authorize)
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");

        // 3. Cada operacion tiene resumen y al menos un error documentado ademas del exito
        paths.fields().forEachRemaining(entry -> {
            String ruta = entry.getKey();
            entry.getValue().fields().forEachRemaining(op -> {
                JsonNode operacion = op.getValue();
                assertThat(operacion.hasNonNull("summary"))
                        .as("%s %s no tiene summary", op.getKey().toUpperCase(), ruta).isTrue();
                assertThat(operacion.get("responses").size())
                        .as("%s %s deberia documentar el exito y al menos un error",
                                op.getKey().toUpperCase(), ruta)
                        .isGreaterThanOrEqualTo(2);
            });
        });

        // 4. Los errores apuntan al esquema unico ApiError
        assertThat(spec.at("/components/schemas/ApiError").isMissingNode()).isFalse();

        exportar(raw);
    }

    /** Las claves se ordenan porque springdoc usa HashMap: sin esto el archivo cambiaria en cada ejecucion. */
    private void exportar(String rawJson) throws Exception {
        Object arbol = objectMapper.readValue(rawJson, Object.class);
        String json = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(arbol);

        Path raiz = Path.of("").toAbsolutePath();
        while (raiz != null && !Files.exists(raiz.resolve("docker-compose.yml"))) {
            raiz = raiz.getParent();
        }
        Path destino = (raiz != null ? raiz.resolve("docs") : Path.of("target"));
        Files.createDirectories(destino);
        Files.writeString(destino.resolve("openapi.json"), json + System.lineSeparator(),
                StandardCharsets.UTF_8);
        System.out.println("OpenAPI exportado a " + destino.resolve("openapi.json"));
    }
}
