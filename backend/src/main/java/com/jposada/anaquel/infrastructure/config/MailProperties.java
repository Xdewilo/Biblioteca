// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String from,
        String fromName,
        String appUrl
) {
    public MailProperties {
        if (from == null || from.isBlank()) from = "hola@anaquel.app";
        if (fromName == null || fromName.isBlank()) fromName = "Anaquel";
        if (appUrl == null || appUrl.isBlank()) appUrl = "http://localhost:5173";
    }
}
