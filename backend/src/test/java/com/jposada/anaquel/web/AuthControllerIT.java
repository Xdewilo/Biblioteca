// by Jeremy Posada
package com.jposada.anaquel.web;

import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API /api/auth - registro, login y BCrypt")
class AuthControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("registro devuelve token y NUNCA la contrasena; en base queda el hash BCrypt")
    void registerReturnsTokenAndStoresBcryptHash() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ana Perez", "email", "ana@anaquel.app", "password", "Secreta123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("ana@anaquel.app"))
                .andExpect(jsonPath("$.user.role").value("BIBLIOTECARIO"))
                .andExpect(jsonPath("$.user.blocked").value(false))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String stored = userRepository.findByEmailIgnoreCase("ana@anaquel.app").orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2");           // prefijo BCrypt
        assertThat(stored).isNotEqualTo("Secreta123"); // jamas en texto plano
    }

    @Test
    @DisplayName("no se puede registrar dos veces el mismo correo")
    void duplicateEmailReturns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ana", "email", "ana@anaquel.app", "password", "Secreta123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    @DisplayName("una contrasena corta se rechaza con el detalle del campo")
    void shortPasswordIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ana", "email", "ana@anaquel.app", "password", "corta"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("login con credenciales correctas devuelve un token usable en /api/auth/me")
    void loginReturnsUsableToken() throws Exception {
        String register = objectMapper.writeValueAsString(Map.of(
                "name", "Ana", "email", "ana@anaquel.app", "password", "Secreta123"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated());

        String login = objectMapper.writeValueAsString(Map.of(
                "email", "ana@anaquel.app", "password", "Secreta123"));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@anaquel.app"));
    }

    @Test
    @DisplayName("login con contrasena incorrecta responde 401 sin revelar si el correo existe")
    void wrongPasswordReturns401() throws Exception {
        String register = objectMapper.writeValueAsString(Map.of(
                "name", "Ana", "email", "ana@anaquel.app", "password", "Secreta123"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register));

        String login = objectMapper.writeValueAsString(Map.of(
                "email", "ana@anaquel.app", "password", "EstaNoEs123"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Correo o contrasena incorrectos."));
    }

    @Test
    @DisplayName("el registro publico ignora el campo role: nadie puede auto-asignarse ADMIN")
    void registerIgnoresRoleField() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Intruso", "email", "intruso@anaquel.app", "password", "Secreta123", "role", "ADMIN"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("BIBLIOTECARIO"));
    }
}
