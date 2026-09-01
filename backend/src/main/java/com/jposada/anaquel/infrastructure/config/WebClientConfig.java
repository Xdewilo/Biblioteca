// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    public static final String OPEN_LIBRARY_CLIENT = "openLibraryWebClient";

    @Bean(name = OPEN_LIBRARY_CLIENT)
    public WebClient openLibraryWebClient(OpenLibraryProperties properties) {
        HttpClient httpClient = HttpClient.create()
                // openlibrary.org responde 302 en /isbn/{isbn}.json; sin followRedirect nunca llegan los datos.
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.timeoutMs())
                .responseTimeout(Duration.ofMillis(properties.timeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.timeoutMs(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(org.springframework.http.HttpHeaders.ACCEPT,
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(org.springframework.http.HttpHeaders.USER_AGENT,
                        "biblioteca-ezertech/1.0 (prueba tecnica)")
                .build();
    }
}
