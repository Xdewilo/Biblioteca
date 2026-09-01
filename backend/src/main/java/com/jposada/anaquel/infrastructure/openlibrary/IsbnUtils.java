// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

/** Normaliza el ISBN para que "978-0-13-468599-1" y "9780134685991" sean el mismo libro. */
public final class IsbnUtils {

    private IsbnUtils() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[^0-9Xx]", "").toUpperCase();
    }

    /** Forma valida (10 o 13 caracteres). No comprueba el digito de control. */
    public static boolean isPlausible(String normalized) {
        return normalized != null && (normalized.length() == 10 || normalized.length() == 13);
    }

    /** Comprueba el digito de control (modulo 11 para ISBN-10, modulo 10 para ISBN-13). */
    public static boolean isValid(String normalized) {
        if (!isPlausible(normalized)) {
            return false;
        }
        return normalized.length() == 10 ? checkIsbn10(normalized) : checkIsbn13(normalized);
    }

    private static boolean checkIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int value;
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c == 'X' && i == 9) {
                value = 10;
            } else {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static boolean checkIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }
}
