// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import com.jposada.anaquel.infrastructure.config.CacheConfig;
import com.jposada.anaquel.infrastructure.config.OpenLibraryProperties;
import com.jposada.anaquel.infrastructure.config.WebClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Nunca propaga excepciones (devuelve UNAVAILABLE) y los fallos de infraestructura no se cachean. */
@Service
@Slf4j
public class OpenLibraryClient {

    private static final Pattern YEAR = Pattern.compile("(1[4-9]\\d{2}|20\\d{2})");
    private static final int MAX_AUTHORS = 3;
    private static final int MAX_SUBJECTS = 8;

    private final WebClient webClient;
    private final OpenLibraryProperties properties;

    public OpenLibraryClient(@Qualifier(WebClientConfig.OPEN_LIBRARY_CLIENT) WebClient webClient,
                             OpenLibraryProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Cacheable(cacheNames = CacheConfig.OPEN_LIBRARY_CACHE,
            key = "T(com.jposada.anaquel.infrastructure.openlibrary.IsbnUtils).normalize(#isbn)",
            unless = "#result.isUnavailable()")
    public LookupResult lookupByIsbn(String isbn) {
        String normalized = IsbnUtils.normalize(isbn);
        if (!IsbnUtils.isPlausible(normalized)) {
            log.debug("ISBN '{}' no tiene forma valida; no se consulta Open Library", isbn);
            return LookupResult.notFound();
        }

        try {
            OpenLibraryResponse response = withResilience(webClient.get()
                    .uri("/isbn/{isbn}.json", normalized)
                    .retrieve()
                    .bodyToMono(OpenLibraryResponse.class))
                    .block();

            if (response == null || response.title() == null || response.title().isBlank()) {
                log.info("Open Library no tiene datos para el ISBN {}", normalized);
                return LookupResult.notFound();
            }
            return LookupResult.found(toExternalBookData(normalized, response));

        } catch (WebClientResponseException.NotFound e) {
            log.info("Open Library devolvio 404 para el ISBN {}", normalized);
            return LookupResult.notFound();

        } catch (Exception e) {
            log.warn("Fallo la consulta a Open Library para el ISBN {}: {}", normalized, e.toString());
            return LookupResult.unavailable();
        }
    }

    private ExternalBookData toExternalBookData(String isbn, OpenLibraryResponse response) {
        String title = response.subtitle() != null && !response.subtitle().isBlank()
                ? response.title() + ": " + response.subtitle()
                : response.title();

        // El work sirve para autores y temas: muchas ediciones no traen ninguno de los dos.
        OpenLibraryResponse.WorkResponse work = needsWork(response) ? fetchWork(response) : null;

        return new ExternalBookData(
                isbn,
                title,
                resolveAuthors(response, work),
                parseYear(response.publishDate()),
                resolveCoverUrl(isbn, response),
                resolveSubjects(response, work));
    }

    private boolean needsWork(OpenLibraryResponse response) {
        boolean missingAuthors = response.authors() == null || response.authors().isEmpty();
        boolean missingSubjects = response.subjects() == null || response.subjects().isEmpty();
        return missingAuthors || missingSubjects;
    }

    private OpenLibraryResponse.WorkResponse fetchWork(OpenLibraryResponse response) {
        if (response.works() == null || response.works().isEmpty() || response.works().get(0).key() == null) {
            return null;
        }
        try {
            return withResilience(webClient.get()
                    .uri(response.works().get(0).key() + ".json")
                    .retrieve()
                    .bodyToMono(OpenLibraryResponse.WorkResponse.class))
                    .block();
        } catch (Exception e) {
            log.debug("No se pudo resolver el work: {}", e.toString());
            return null;
        }
    }

    private String resolveAuthors(OpenLibraryResponse response, OpenLibraryResponse.WorkResponse work) {
        List<String> keys = new ArrayList<>();

        if (response.authors() != null) {
            response.authors().stream()
                    .filter(ref -> ref != null && ref.key() != null)
                    .forEach(ref -> keys.add(ref.key()));
        }
        if (keys.isEmpty() && work != null && work.authors() != null) {
            work.authors().stream()
                    .filter(entry -> entry != null && entry.author() != null && entry.author().key() != null)
                    .forEach(entry -> keys.add(entry.author().key()));
        }
        if (keys.isEmpty()) {
            return null;
        }

        List<String> names = new ArrayList<>();
        for (String key : keys.stream().limit(MAX_AUTHORS).toList()) {
            try {
                OpenLibraryResponse.AuthorResponse author = withResilience(webClient.get()
                        .uri(key + ".json")
                        .retrieve()
                        .bodyToMono(OpenLibraryResponse.AuthorResponse.class))
                        .block();
                if (author != null && author.bestName() != null) {
                    names.add(author.bestName());
                }
            } catch (Exception e) {
                log.debug("No se pudo resolver el autor {}: {}", key, e.toString());
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** Los temas suelen colgar del "work", no de la edicion concreta. */
    private List<String> resolveSubjects(OpenLibraryResponse response, OpenLibraryResponse.WorkResponse work) {
        if (response.subjects() != null && !response.subjects().isEmpty()) {
            return response.subjects().stream().limit(MAX_SUBJECTS).toList();
        }
        if (work != null && work.subjects() != null) {
            return work.subjects().stream().limit(MAX_SUBJECTS).toList();
        }
        return List.of();
    }

    /** Sin portada declarada, covers.openlibrary.org devuelve una imagen vacia. */
    private String resolveCoverUrl(String isbn, OpenLibraryResponse response) {
        if (response.covers() == null || response.covers().isEmpty()) {
            return null;
        }
        return "%s/b/isbn/%s-L.jpg".formatted(properties.coversBaseUrl(), isbn);
    }

    /** Un 404 no se reintenta: el ISBN sencillamente no esta. */
    private <T> Mono<T> withResilience(Mono<T> call) {
        Mono<T> withTimeout = call.timeout(Duration.ofMillis(properties.timeoutMs()));
        if (properties.maxRetries() <= 0) {
            return withTimeout;
        }
        return withTimeout.retryWhen(
                Retry.backoff(properties.maxRetries(), Duration.ofMillis(200))
                        .filter(OpenLibraryClient::isTransient)
                        .transientErrors(true));
    }

    private static boolean isTransient(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == 429;
        }
        return error instanceof java.util.concurrent.TimeoutException
                || error instanceof java.io.IOException
                || error instanceof io.netty.handler.timeout.ReadTimeoutException;
    }

    private Integer parseYear(String publishDate) {
        if (publishDate == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(publishDate);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
