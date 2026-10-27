package com.br.spbemestar.controller;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.service.PhotoPrecheck;
import com.br.spbemestar.service.CitizenAccountService;
import com.br.spbemestar.service.ReporterTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A conferência da foto na hora do envio, com o classificador simulado (o HTTP dele tem teste próprio). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportPrecheckTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GridFsTemplate gridFs;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired CitizenAccountService citizens;
    @MockitoBean PhotoPrecheck precheck;

    @Test
    void refusesAPhotoOfAnotherKindAndSuggestsIt() {
        sees("TRASH", 0.93);
        int photosBefore = storedPhotos();

        MvcTestResult result = send("POTHOLE", -28.00, false);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("A foto não combina com o tipo escolhido");
        assertThat(result).bodyJson().extractingPath("$.chosenCategory").isEqualTo("POTHOLE");
        assertThat(result).bodyJson().extractingPath("$.predictedCategory").isEqualTo("TRASH");
        assertThat(result).bodyJson().extractingPath("$.predictedCategoryName")
                .isEqualTo("Descarte irregular de lixo ou entulho");
        // recusado antes de gravar: nem relato, nem foto
        assertThat(reportsAt(-28.00)).isZero();
        assertThat(storedPhotos()).isEqualTo(photosBefore);
    }

    @Test
    void refusesAPhotoWithoutAnyStreetProblem() {
        sees("OTHER", 0.97);

        MvcTestResult result = send("POTHOLE", -28.01, false);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.predictedCategory").isEqualTo("OTHER");
        assertThat(result).bodyJson().extractingPath("$.predictedCategoryName").isNull();
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("A foto não parece mostrar um problema de rua.");
    }

    @Test
    void acceptsWhenTheModelAgreesOrIsUnsure() {
        sees("POTHOLE", 0.95);
        assertThat(send("POTHOLE", -28.02, false)).hasStatus(HttpStatus.CREATED);

        // com pouca confiança, quem decide é a moderação, não o envio
        sees("TRASH", 0.62);
        assertThat(send("POTHOLE", -28.03, false)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void acceptsWhenThePersonInsistsWithoutAskingTheModel() {
        sees("TRASH", 0.99);

        assertThat(send("POTHOLE", -28.04, true)).hasStatus(HttpStatus.CREATED);

        verify(precheck, never()).classify(any(), anyString());
        assertThat(reportsAt(-28.04)).isEqualTo(1);
    }

    @Test
    void acceptsWhenTheClassifierIsUnavailable() {
        when(precheck.classify(any(), anyString())).thenReturn(Optional.empty());

        assertThat(send("POTHOLE", -28.05, false)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void ignoresAKindTheCityDoesNotUse() {
        sees("SOMETHING_ELSE", 0.99);

        assertThat(send("POTHOLE", -28.06, false)).hasStatus(HttpStatus.CREATED);
    }

    private void sees(String category, double confidence) {
        when(precheck.classify(any(), anyString()))
                .thenReturn(Optional.of(new PhotoPrecheck.Prediction(category, confidence)));
    }

    private MvcTestResult send(String category, double latitude, boolean confirmCategory) {
        var request = mvc.post().uri("/api/v1/reports").multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(citizens))
                .file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", TestPhotos.jpeg()))
                .param("latitude", String.valueOf(latitude))
                .param("longitude", "-48.00")
                .param("category", category);
        if (confirmCategory) {
            request = request.param("confirmCategory", "true");
        }
        return request.exchange();
    }

    private int reportsAt(double latitude) {
        return jdbc.queryForObject("SELECT count(*) FROM report WHERE abs(ST_Y(location::geometry) - ?) < 0.0001",
                Integer.class, latitude);
    }

    private int storedPhotos() {
        return gridFs.find(new Query()).into(new ArrayList<>()).size();
    }
}
