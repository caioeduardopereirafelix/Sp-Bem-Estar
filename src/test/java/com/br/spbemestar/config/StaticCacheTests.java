package com.br.spbemestar.config;

import com.br.spbemestar.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaticCacheTests {

    @Autowired MockMvcTester mvc;

    @Test
    void scriptsAndStylesAreRevalidatedByContentAndAnswer304WhenUnchanged() {
        for (String path : new String[]{"/map.css", "/index.html", "/sw.js"}) {
            MvcTestResult first = mvc.get().uri(path).exchange();
            String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

            assertThat(first).hasStatusOk();
            assertThat(first.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
            assertThat(first.getResponse().getHeader(HttpHeaders.LAST_MODIFIED)).isNull();
            assertThat(etag).isNotBlank();
            assertThat(mvc.get().uri(path).header(HttpHeaders.IF_NONE_MATCH, etag).exchange())
                    .hasStatus(HttpStatus.NOT_MODIFIED);
        }
    }

    @Test
    void fontsAreCachedForAMonth() {
        MvcTestResult font = mvc.get().uri("/fonts/atkinson-400.woff2").exchange();

        assertThat(font).hasStatusOk();
        assertThat(font.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("max-age=2592000, public");
    }

    @Test
    void apiAnswersStayOutOfTheCache() {
        assertThat(mvc.get().uri("/api/v1/categories").exchange().getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
    }
}
