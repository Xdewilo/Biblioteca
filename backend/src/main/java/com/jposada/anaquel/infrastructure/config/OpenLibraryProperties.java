// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openlibrary")
public record OpenLibraryProperties(
        String baseUrl,
        String coversBaseUrl,
        long timeoutMs,
        /** Reintentos ante fallos transitorios (timeout, 5xx). 0 los desactiva. */
        int maxRetries
) {
    public OpenLibraryProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openlibrary.org";
        }
        if (coversBaseUrl == null || coversBaseUrl.isBlank()) {
            coversBaseUrl = "https://covers.openlibrary.org";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 5000;
        }
        if (maxRetries < 0) {
            maxRetries = 0;
        }
    }
}
