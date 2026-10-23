package com.br.spbemestar.config;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.service.ReporterTokenService;
import com.br.testsupport.FailingController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FailingController.class})
class TracingTests {

    static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    static final String TRACE_PARENT = "00-" + TRACE_ID + "-b7ad6b7169203331-01";

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReporterTokenService reporterTokens;

    @Test
    void everyResponseCarriesTheTraceId() {
        MvcTestResult result = mvc.get().uri("/api/v1/categories").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(TraceIdResponseFilter.HEADER)).matches("[0-9a-f]{32}");
        // inclusive nas recusas da segurança
        assertThat(mvc.delete().uri("/api/v1/issues/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED)
                .headers().containsHeader(TraceIdResponseFilter.HEADER);
    }

    @Test
    void continuesTheTraceSentByTheCaller() {
        MvcTestResult result = mvc.get().uri("/api/v1/categories").header("traceparent", TRACE_PARENT).exchange();

        assertThat(result).hasHeader(TraceIdResponseFilter.HEADER, TRACE_ID);
    }

    @Test
    void reportEventKeepsTheTraceUntilItIsPublished() {
        MvcTestResult created = mvc.post().uri("/api/v1/reports").multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(reporterTokens))
                .header("traceparent", TRACE_PARENT)
                .file("photo", TestPhotos.jpeg())
                .param("category", "TRASH").param("latitude", "-29.00").param("longitude", "-49.00")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Long reportId = ((Number) JsonPath.read(body(created), "$.reportId")).longValue();

        String stored = jdbc.queryForObject("""
                SELECT trace_parent FROM outbox_event WHERE type = 'ReportCreated' AND aggregate_id = ?
                """, String.class, reportId);
        assertThat(stored).startsWith("00-" + TRACE_ID + "-");
    }

    @Test
    void unexpectedErrorsHideDetailsAndShowTheTraceId() {
        MvcTestResult result = mvc.get().uri("/api/v1/categories/falha-de-teste")
                .header("traceparent", TRACE_PARENT).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Erro interno");
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(TRACE_ID);
        assertThat(body(result)).contains(TRACE_ID).doesNotContain("senha-do-banco", "IllegalStateException");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
