// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import com.jposada.anaquel.infrastructure.openlibrary.ExternalBookData;
import com.jposada.anaquel.infrastructure.openlibrary.LookupResult;
import com.jposada.anaquel.infrastructure.openlibrary.OpenLibraryClient;

import com.jposada.anaquel.infrastructure.config.OpenLibraryProperties;
import com.jposada.anaquel.infrastructure.config.WebClientConfig;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenLibraryClient - respuestas simuladas de la API externa")
class OpenLibraryClientTest {

    private MockWebServer server;
    private OpenLibraryClient client;

    private static final String EDITION_JSON = """
            {
              "title": "Effective Java",
              "publish_date": "Jan 06, 2018",
              "covers": [8739161],
              "authors": [{"key": "/authors/OL233597A"}],
              "works": [{"key": "/works/OL15358691W"}],
              "number_of_pages": 412
            }
            """;

    private static final String AUTHOR_JSON = """
            {"name": "Joshua Bloch", "personal_name": "Joshua Bloch"}
            """;

    private static final String WORK_JSON = """
            {
              "title": "Effective Java",
              "subjects": ["Java (Computer program language)", "Programming"],
              "authors": [{"author": {"key": "/authors/OL233597A"}, "type": {"key": "/type/author_role"}}]
            }
            """;

    /** Caso real de 9780134685991: la edicion NO declara authors, viven en el work. */
    private static final String EDITION_WITHOUT_AUTHORS_JSON = """
            {
              "title": "Effective Java",
              "publish_date": "December 27, 2017",
              "covers": [8739161],
              "works": [{"key": "/works/OL6223299W"}]
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OpenLibraryProperties properties = new OpenLibraryProperties(
                server.url("/").toString(), "https://covers.openlibrary.org", 1500, 0);
        // Se usa exactamente el mismo WebClient de produccion (timeouts, redirecciones, headers).
        WebClient webClient = new WebClientConfig().openLibraryWebClient(properties);
        client = new OpenLibraryClient(webClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("mapea titulo, autor resuelto, anio, portada y temas del work")
    void mapsFullResponse() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/isbn/")) return json(EDITION_JSON);
                if (path.startsWith("/authors/")) return json(AUTHOR_JSON);
                if (path.startsWith("/works/")) return json(WORK_JSON);
                return new MockResponse().setResponseCode(404);
            }
        });

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isFound()).isTrue();
        ExternalBookData data = result.data();
        assertThat(data.title()).isEqualTo("Effective Java");
        assertThat(data.author()).isEqualTo("Joshua Bloch");
        assertThat(data.publicationYear()).isEqualTo(2018);
        assertThat(data.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg");
        assertThat(data.subjects()).contains("Programming");
    }

    @Test
    @DisplayName("un ISBN que Open Library no conoce devuelve NOT_FOUND, no un error")
    void unknownIsbnReturnsNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404));

        LookupResult result = client.lookupByIsbn("9999999999999");

        assertThat(result.status()).isEqualTo(LookupResult.Status.NOT_FOUND);
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.asOptional()).isEmpty();
    }

    @Test
    @DisplayName("si la API responde 500 el cliente NO revienta: devuelve UNAVAILABLE")
    void serverErrorReturnsUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isUnavailable()).isTrue();
        assertThat(result.asOptional()).isEmpty();
    }

    @Test
    @DisplayName("si la API tarda mas que el timeout devuelve UNAVAILABLE en vez de colgar la peticion")
    void timeoutReturnsUnavailable() {
        server.enqueue(json(EDITION_JSON).setBodyDelay(Duration.ofSeconds(5).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS));

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isUnavailable()).isTrue();
    }

    @Test
    @DisplayName("un JSON inesperado tampoco rompe: se degrada a UNAVAILABLE")
    void malformedJsonReturnsUnavailable() {
        server.enqueue(json("{ esto no es json valido "));

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isUnavailable()).isTrue();
    }

    @Test
    @DisplayName("una respuesta sin titulo se trata como NOT_FOUND")
    void responseWithoutTitleIsNotFound() {
        server.enqueue(json("{\"number_of_pages\": 100}"));

        assertThat(client.lookupByIsbn("9780134685991").status())
                .isEqualTo(LookupResult.Status.NOT_FOUND);
    }

    @Test
    @DisplayName("no hay portada si Open Library no declara ninguna")
    void noCoverWhenApiDoesNotDeclareOne() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/isbn/")) return json("{\"title\": \"Sin portada\"}");
                return new MockResponse().setResponseCode(404);
            }
        });

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isFound()).isTrue();
        assertThat(result.data().coverUrl()).isNull();
    }

    @Test
    @DisplayName("si la edicion no trae autores, los resuelve desde el work")
    void resolvesAuthorsFromWorkWhenEditionHasNone() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/isbn/")) return json(EDITION_WITHOUT_AUTHORS_JSON);
                if (path.startsWith("/works/")) return json(WORK_JSON);
                if (path.startsWith("/authors/")) return json(AUTHOR_JSON);
                return new MockResponse().setResponseCode(404);
            }
        });

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isFound()).isTrue();
        assertThat(result.data().author()).isEqualTo("Joshua Bloch");
        assertThat(result.data().subjects()).contains("Programming");
    }

    @Test
    @DisplayName("sigue la redireccion 302 que Open Library devuelve en /isbn/{isbn}.json")
    void followsTheRedirectOpenLibraryReturns() {
        // La API real responde 302 hacia /books/OL...json: sin followRedirect nunca llegan los datos.
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/isbn/")) {
                    return new MockResponse().setResponseCode(302)
                            .setHeader("Location", "/books/OL31838212M.json");
                }
                if (path.startsWith("/books/")) return json(EDITION_JSON);
                if (path.startsWith("/authors/")) return json(AUTHOR_JSON);
                if (path.startsWith("/works/")) return json(WORK_JSON);
                return new MockResponse().setResponseCode(404);
            }
        });

        LookupResult result = client.lookupByIsbn("9780134685991");

        assertThat(result.isFound()).isTrue();
        assertThat(result.data().title()).isEqualTo("Effective Java");
    }

    @Test
    @DisplayName("un ISBN con forma invalida ni siquiera llega a la red")
    void invalidIsbnSkipsTheNetworkCall() {
        LookupResult result = client.lookupByIsbn("123");

        assertThat(result.status()).isEqualTo(LookupResult.Status.NOT_FOUND);
        assertThat(server.getRequestCount()).isZero();
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
