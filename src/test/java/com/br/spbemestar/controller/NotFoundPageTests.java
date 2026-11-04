package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotFoundPageTests {

    static final String BROWSER = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Autowired MockMvcTester mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void aMissingPageShowsTheNotFoundPageInTheBrowser() {
        for (String path : new String[]{"/pagina-que-nao-existe", "/a/b/c", "/relatos.html"}) {
            assertThat(mvc.get().uri(path).header(HttpHeaders.ACCEPT, BROWSER).exchange())
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                    .bodyText().contains("Página não encontrada").contains("href=\"/\"")
                    .doesNotContain("Entre com o seu e-mail");
        }
    }

    @Test
    void theApiKeepsAnsweringWithJson() {
        assertThat(mvc.get().uri("/api/v1/rota-que-nao-existe").header(HttpHeaders.ACCEPT, BROWSER).exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(mvc.get().uri("/pagina-que-nao-existe").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void openingUnknownPagesDoesNotOpenProtectedRoutes() {
        assertThat(mvc.get().uri("/api/v1/issues/queue").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/operators").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/ceo/dashboard").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/env").header(HttpHeaders.ACCEPT, BROWSER).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        // só GET: um envio para um endereço que não existe continua pedindo login
        assertThat(mvc.post().uri("/pagina-que-nao-existe").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        // e o operador continua precisando do token para a fila, mesmo pelo navegador
        assertThat(mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.ACCEPT, BROWSER)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users)).exchange()).hasStatusOk();
    }
}
