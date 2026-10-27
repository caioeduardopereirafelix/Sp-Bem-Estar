package com.br.spbemestar.classifier.controller;

import com.br.spbemestar.classifier.TestImages;
import com.br.spbemestar.classifier.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// a senha da API é gerada a cada execução: nada de segredo fixo no repositório
@SpringBootTest(properties = {
        "classifier.model=classpath:models/color-test.onnx",
        "classifier.model-version=color-test",
        "app.classify-api.username=api-teste"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClassifyControllerTests {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired MockMvcTester mvc;

    @DynamicPropertySource
    static void apiPassword(DynamicPropertyRegistry registry) {
        registry.add("app.classify-api.password", () -> PASSWORD);
    }

    @Test
    void classifiesThePhotoForTheApi() {
        // o modelo de teste classifica pela cor: vermelho é POTHOLE, azul é OTHER
        MvcTestResult red = classify(TestImages.jpeg(Color.RED), apiAuth());
        assertThat(red).hasStatusOk().bodyJson().extractingPath("$.label").isEqualTo("POTHOLE");
        assertThat(red).bodyJson().extractingPath("$.modelVersion").isEqualTo("color-test");
        assertThat(red).bodyJson().extractingPath("$.confidence").asNumber().satisfies(
                confidence -> assertThat(confidence.doubleValue()).isBetween(0.0, 1.0));

        assertThat(classify(TestImages.jpeg(Color.BLUE), apiAuth())).hasStatusOk()
                .bodyJson().extractingPath("$.label").isEqualTo("OTHER");
    }

    @Test
    void onlyTheApiClassifies() {
        assertThat(classify(TestImages.jpeg(Color.RED), null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(classify(TestImages.jpeg(Color.RED), basic("api-teste", UUID.randomUUID().toString())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsSomethingThatIsNotAPhoto() {
        assertThat(mvc.post().uri("/api/classify").contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.AUTHORIZATION, apiAuth())
                .content("não é uma imagem".getBytes(StandardCharsets.UTF_8)).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult classify(byte[] photo, String authorization) {
        var request = mvc.post().uri("/api/classify").contentType(MediaType.IMAGE_JPEG).content(photo);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private String apiAuth() {
        return basic("api-teste", PASSWORD);
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
