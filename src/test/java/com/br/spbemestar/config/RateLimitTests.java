package com.br.spbemestar.config;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada teste usa IPs próprios, porque os contadores duram o contexto inteiro. */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules[0].name=login",
        "app.rate-limit.rules[0].method=POST",
        "app.rate-limit.rules[0].path=/api/v1/auth/login",
        "app.rate-limit.rules[0].capacity=2",
        "app.rate-limit.rules[0].period=1h",
        "app.rate-limit.rules[1].name=api",
        "app.rate-limit.rules[1].path=/api/**",
        "app.rate-limit.rules[1].capacity=3",
        "app.rate-limit.rules[1].period=1h",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTests {

    @Autowired MockMvcTester mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void loginIsLimitedPerIpEvenWithTheRightPassword() {
        TestOperators.bearer(auth, users);
        String ip = "203.0.113.10";

        assertThat(login(ip, "senha-errada-123")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(ip, "senha-errada-456")).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult blocked = login(ip, TestOperators.PASSWORD);

        assertThat(blocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.title").isEqualTo("Muitas requisições");
        assertThat(Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(login("203.0.113.11", TestOperators.PASSWORD)).hasStatusOk();
    }

    @Test
    void limitIsCheckedBeforeAuthentication() {
        String ip = "203.0.113.20";

        for (int remaining = 2; remaining >= 0; remaining--) {
            assertThat(delete(ip)).hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasHeader(RateLimitFilter.LIMIT_HEADER, "3")
                    .hasHeader(RateLimitFilter.REMAINING_HEADER, String.valueOf(remaining));
        }
        assertThat(delete(ip)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void eachRuleHasItsOwnCounter() {
        String ip = "203.0.113.30";

        for (int i = 0; i < 3; i++) {
            assertThat(categories(ip)).hasStatusOk();
        }
        assertThat(categories(ip)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login(ip, "senha-errada-123")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ipv6ClientsAreCountedByTheirSlash64() {
        for (String ip : new String[]{"2001:db8:1:2::1", "2001:db8:1:2::2", "2001:db8:1:2:aaaa:bbbb:cccc:dddd"}) {
            assertThat(categories(ip)).hasStatusOk();
        }
        assertThat(categories("2001:db8:1:2::ffff")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(categories("2001:db8:1:3::1")).hasStatusOk();
    }

    @Test
    void mapAndMonitoringAreNotLimited() {
        String ip = "203.0.113.40";

        for (int i = 0; i < 10; i++) {
            assertThat(get(ip, "/")).hasStatusOk().doesNotContainHeader(RateLimitFilter.LIMIT_HEADER);
            assertThat(get(ip, "/actuator/health")).hasStatusOk();
        }
    }

    private MvcTestResult login(String ip, String password) {
        return mvc.post().uri("/api/v1/auth/login").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(TestOperators.EMAIL, password))
                .exchange();
    }

    private MvcTestResult delete(String ip) {
        return mvc.delete().uri("/api/v1/issues/1").with(from(ip)).exchange();
    }

    private MvcTestResult categories(String ip) {
        return get(ip, "/api/v1/categories");
    }

    private MvcTestResult get(String ip, String path) {
        return mvc.get().uri(path).with(from(ip)).exchange();
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
