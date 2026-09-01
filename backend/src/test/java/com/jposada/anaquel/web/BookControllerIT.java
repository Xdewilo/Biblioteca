// by Jeremy Posada
package com.jposada.anaquel.web;

import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API /api/books - seguridad, roles y errores consistentes")
class BookControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookRepository bookRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String adminToken;
    private String librarianToken;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
        userRepository.deleteAll();

        AppUser admin = userRepository.save(AppUser.builder()
                .name("Admin").email("admin@anaquel.app")
                .passwordHash(passwordEncoder.encode("Admin123*")).role(Role.ADMIN).build());
        AppUser librarian = userRepository.save(AppUser.builder()
                .name("Biblio").email("biblio@biblioteca.co")
                .passwordHash(passwordEncoder.encode("Biblio123*")).role(Role.BIBLIOTECARIO).build());

        adminToken = "Bearer " + jwtService.generateToken(admin);
        librarianToken = "Bearer " + jwtService.generateToken(librarian);

        bookRepository.save(Book.builder()
                .title("Clean Code").author("Robert C. Martin").isbn("9780132350884")
                .publicationYear(2008).status(BookStatus.DISPONIBLE).build());
        bookRepository.save(Book.builder()
                .title("Cien anos de soledad").author("Gabriel Garcia Marquez").isbn("9780307474728")
                .publicationYear(1967).status(BookStatus.PRESTADO).build());
    }

    @Test
    @DisplayName("GET /api/books sin token responde 401 con el formato de error de la API")
    void listWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/books"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/books con token devuelve el catalogo paginado")
    void listReturnsCatalog() throws Exception {
        mockMvc.perform(get("/api/books").header("Authorization", librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].isbn").exists());
    }

    @Test
    @DisplayName("GET /api/books?search filtra por titulo, autor o ISBN")
    void listFiltersBySearch() throws Exception {
        mockMvc.perform(get("/api/books").param("search", "garcia").header("Authorization", librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Cien anos de soledad"));

        mockMvc.perform(get("/api/books").param("search", "9780132350884").header("Authorization", librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/books?status filtra por estado")
    void listFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/books").param("status", "PRESTADO").header("Authorization", librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PRESTADO"));
    }

    @Test
    @DisplayName("POST /api/books con rol BIBLIOTECARIO responde 403")
    void createRequiresAdminRole() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "isbn", "9780201633610", "title", "Design Patterns",
                "author", "Erich Gamma", "autocomplete", false));

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/books con rol ADMIN crea el libro")
    void adminCreatesBook() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "isbn", "978-0-201-63361-0", "title", "Design Patterns",
                "author", "Erich Gamma", "publicationYear", 1994, "autocomplete", false));

        mockMvc.perform(post("/api/books")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("9780201633610"))
                .andExpect(jsonPath("$.status").value("DISPONIBLE"))
                .andExpect(jsonPath("$.enrichedFromExternal").value(false));
    }

    @Test
    @DisplayName("POST /api/books con ISBN duplicado responde 409 DUPLICATE_ISBN")
    void duplicateIsbnReturns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "isbn", "9780132350884", "title", "Otro titulo",
                "author", "Otro autor", "autocomplete", false));

        mockMvc.perform(post("/api/books")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ISBN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("9780132350884")));
    }

    @Test
    @DisplayName("POST /api/books sin ISBN responde 400 con el detalle campo por campo")
    void validationErrorsAreDetailed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "Sin ISBN", "author", "Nadie"));

        mockMvc.perform(post("/api/books")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("isbn"));
    }

    @Test
    @DisplayName("DELETE /api/books/{id} no borra un libro PRESTADO")
    void cannotDeleteLoanedBook() throws Exception {
        Long loanedId = bookRepository.findByIsbn("9780307474728").orElseThrow().getId();

        mockMvc.perform(delete("/api/books/{id}", loanedId).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("DELETE /api/books/{id} borra un libro DISPONIBLE")
    void adminDeletesAvailableBook() throws Exception {
        Long availableId = bookRepository.findByIsbn("9780132350884").orElseThrow().getId();

        mockMvc.perform(delete("/api/books/{id}", availableId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/books/{id} inexistente responde 404 NOT_FOUND")
    void unknownBookReturns404() throws Exception {
        mockMvc.perform(get("/api/books/{id}", 999_999).header("Authorization", librarianToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/books/lookup/{isbn} responde 503 cuando Open Library no esta disponible")
    void lookupDegradesGracefully() throws Exception {
        // El perfil de test apunta a un host muerto: se ejercita la ruta de fallo real.
        mockMvc.perform(get("/api/books/lookup/{isbn}", "9780134685991").header("Authorization", librarianToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXTERNAL_LOOKUP_FAILED"));
    }

    @Test
    @DisplayName("un token invalido no autentica")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/books").header("Authorization", "Bearer token.falso.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/books con un ISBN cuyo digito de control no cuadra responde 400 INVALID_ISBN")
    void invalidIsbnCheckDigitReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "isbn", "9780132350885", "title", "Clean Code", "author", "Robert C. Martin"));

        mockMvc.perform(post("/api/books").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ISBN"));
    }

    @Test
    @DisplayName("una ruta que no existe responde 404 con el formato de error de la API, no 500")
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/api/no-existe").header("Authorization", librarianToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/no-existe"));
    }
}
