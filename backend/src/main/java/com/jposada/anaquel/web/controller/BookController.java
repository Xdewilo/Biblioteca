// by Jeremy Posada
package com.jposada.anaquel.web.controller;

import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.application.book.EliminarLibro;
import com.jposada.anaquel.application.book.EliminarLibroHandler;
import com.jposada.anaquel.application.book.RegistrarLibro;
import com.jposada.anaquel.application.book.RegistrarLibroHandler;
import com.jposada.anaquel.application.book.query.BuscarCatalogo;
import com.jposada.anaquel.application.book.query.BuscarCatalogoHandler;
import com.jposada.anaquel.application.book.query.ConsultarLibro;
import com.jposada.anaquel.application.book.query.ConsultarLibroHandler;
import com.jposada.anaquel.application.book.query.PrevisualizarIsbn;
import com.jposada.anaquel.application.book.query.PrevisualizarIsbnHandler;
import com.jposada.anaquel.web.dto.BookLookupResponse;
import com.jposada.anaquel.web.dto.BookResponse;
import com.jposada.anaquel.web.dto.CreateBookRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.jposada.anaquel.web.dto.ApiError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Catalogo", description = "Libros, busqueda y autocompletado desde Open Library")
public class BookController {

    private final BuscarCatalogoHandler buscarCatalogo;
    private final ConsultarLibroHandler consultarLibro;
    private final PrevisualizarIsbnHandler previsualizarIsbn;
    private final RegistrarLibroHandler registrarLibro;
    private final EliminarLibroHandler eliminarLibro;

    @GetMapping
    @Operation(summary = "Lista el catalogo con busqueda por titulo/autor/ISBN y filtro por estado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de libros. Usa search para filtrar por titulo, autor o ISBN"),
        @ApiResponse(responseCode = "401", description = "Falta el token",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Page<BookResponse> list(
            @Parameter(description = "Texto libre: busca en titulo, autor e ISBN, sin distinguir mayusculas",
                    example = "clean")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filtra por estado del ejemplar")
            @RequestParam(required = false) BookStatus status,

            @Parameter(description = "Numero de pagina, empezando en 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Tamano de pagina, maximo 100", example = "12")
            @RequestParam(defaultValue = "20") int size) {
        var paginacion = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("title").ascending());
        return buscarCatalogo.handle(new BuscarCatalogo(search, status, paginacion));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un libro")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "El libro",
                content = @Content(schema = @Schema(implementation = BookResponse.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un libro con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public BookResponse getById(@PathVariable Long id) {
        return consultarLibro.handle(new ConsultarLibro(id));
    }

    @GetMapping("/lookup/{isbn}")
    @Operation(summary = "Previsualiza los datos del libro desde Open Library sin guardar nada",
            description = "404 si el ISBN no existe alla; 503 si la API externa no respondio a tiempo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos traidos de Open Library. NO se guarda nada: es solo la previsualizacion",
                content = @Content(schema = @Schema(implementation = BookLookupResponse.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - Open Library no conoce ese ISBN. Registra el libro a mano",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "EXTERNAL_LOOKUP_FAILED - la API externa no respondio a tiempo. El libro se puede registrar igual a mano",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public BookLookupResponse lookup(@PathVariable String isbn) {
        return previsualizarIsbn.handle(new PrevisualizarIsbn(isbn));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registra un libro (ADMIN). Completa los campos vacios desde Open Library")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Libro registrado. enrichedFromExternal indica si los datos vinieron de Open Library",
                content = @Content(schema = @Schema(implementation = BookResponse.class))),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (falta el ISBN) o MISSING_BOOK_DATA (la API fallo y no escribiste titulo ni autor)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "DUPLICATE_ISBN - ya existe un libro con ese ISBN",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest request) {
        var comando = new RegistrarLibro(request.isbn(), request.title(), request.author(),
                request.publicationYear(), request.coverUrl(), request.subjects(),
                request.autocompleteEnabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(registrarLibro.handle(comando));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Elimina un libro (ADMIN). Solo si esta DISPONIBLE")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Libro eliminado"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN - tu rol no es ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un libro con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "BOOK_NOT_DELETABLE - el libro esta PRESTADO o RESERVADO, o tiene un prestamo activo",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eliminarLibro.handle(new EliminarLibro(id));
        return ResponseEntity.noContent().build();
    }
}
