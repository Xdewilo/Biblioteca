// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import java.io.Serializable;
import java.util.Optional;

/** FOUND / NOT_FOUND (404, se pide llenar a mano) / UNAVAILABLE (503, no se cachea). */
public record LookupResult(Status status, ExternalBookData data) implements Serializable {

    public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

    public static LookupResult found(ExternalBookData data) {
        return new LookupResult(Status.FOUND, data);
    }

    public static LookupResult notFound() {
        return new LookupResult(Status.NOT_FOUND, null);
    }

    public static LookupResult unavailable() {
        return new LookupResult(Status.UNAVAILABLE, null);
    }

    public boolean isFound() {
        return status == Status.FOUND && data != null;
    }

    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE;
    }

    public Optional<ExternalBookData> asOptional() {
        return isFound() ? Optional.of(data) : Optional.empty();
    }
}
