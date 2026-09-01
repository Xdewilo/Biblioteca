// by Jeremy Posada
package com.jposada.anaquel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AnaquelApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnaquelApplication.class, args);
    }
}
