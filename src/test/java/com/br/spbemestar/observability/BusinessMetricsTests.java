package com.br.spbemestar.observability;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ClassificationService;
import com.br.spbemestar.service.ReportService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BusinessMetricsTests {

    @Autowired MockMvcTester mvc;
    @Autowired MeterRegistry meters;
    @Autowired ReportService reportService;
    @Autowired ClassificationService classification;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void countsReportsAndTheirClassification() {
        double created = count("spbemestar.reports.created", "category", "SEWAGE");
        double mismatches = count("spbemestar.reports.classified", "status", "MISMATCH");

        Long reportId = reportService.create(TestReporters.ID, "SEWAGE", -29.50, -49.50, TestPhotos.jpeg()).reportId();
        classification.apply(reportId, "TRASH", 0.97);

        assertThat(count("spbemestar.reports.created", "category", "SEWAGE")).isEqualTo(created + 1);
        assertThat(count("spbemestar.reports.classified", "status", "MISMATCH")).isEqualTo(mismatches + 1);
    }

    @Test
    void countsLoginsWithoutLoggingTheEmail(CapturedOutput output) {
        TestOperators.bearer(auth, users);
        double failures = count("spbemestar.auth.logins", "result", "failure");
        double successes = count("spbemestar.auth.logins", "result", "success");

        assertThat(login(TestOperators.EMAIL, "senha-errada-999")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(TestOperators.EMAIL, TestOperators.PASSWORD)).hasStatusOk();

        assertThat(count("spbemestar.auth.logins", "result", "failure")).isEqualTo(failures + 1);
        assertThat(count("spbemestar.auth.logins", "result", "success")).isEqualTo(successes + 1);
        // o e-mail é dado pessoal: numa força bruta, a lista de tentativas no log vazaria quem tem conta
        assertThat(output.getOut()).contains("Login recusado").doesNotContain(TestOperators.EMAIL)
                .doesNotContain("senha-errada-999");
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)).exchange();
    }

    private double count(String name, String tag, String value) {
        Counter counter = meters.find(name).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }
}
