package com.br.spbemestar.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void refillsGraduallyAndTellsWhenToComeBack() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties.Rule("api", null, "/api/**", 2, Duration.ofMinutes(1)));

        assertThat(call(filter).getStatus()).isEqualTo(200);
        assertThat(call(filter).getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = call(filter);
        assertThat(blocked.getStatus()).isEqualTo(429);
        // capacidade 2 por minuto: uma ficha volta a cada 30 s
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");

        clock.advance(Duration.ofSeconds(29));
        assertThat(call(filter).getStatus()).isEqualTo(429);
        clock.advance(Duration.ofSeconds(1));
        assertThat(call(filter).getStatus()).isEqualTo(200);
        assertThat(call(filter).getStatus()).isEqualTo(429);
    }

    @Test
    void requestsOutsideEveryRulePassUntouched() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties.Rule("login", "post", "/api/v1/auth/login", 1, Duration.ofMinutes(1)));

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = call(filter);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isNull();
        }
    }

    @Test
    void groupsIpv6ByPrefixAndKeepsIpv4AsIs() {
        assertThat(RateLimitFilter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2:3:4:5:6")).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2::9")).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(RateLimitFilter.clientKey("fe80::1%eth0")).isEqualTo("fe80:0:0:0:0:0:0:0/64");
        assertThat(RateLimitFilter.clientKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void rejectsInvalidRulesAtStartup() {
        assertThatThrownBy(() -> filter(new RateLimitProperties.Rule("api", null, "/api/**", 0, Duration.ofMinutes(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> filter(new RateLimitProperties.Rule("api", null, "/api/**", 5, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private RateLimitFilter filter(RateLimitProperties.Rule rule) {
        return new RateLimitFilter(new RateLimitProperties(true, 1000, List.of(rule)), clock,
                JsonMapper.builder().build());
    }

    private static MockHttpServletResponse call(RateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        request.setRemoteAddr("203.0.113.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
