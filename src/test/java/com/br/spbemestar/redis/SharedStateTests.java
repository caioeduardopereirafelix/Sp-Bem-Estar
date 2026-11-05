package com.br.spbemestar.redis;

import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.RateLimitFilter;
import com.br.spbemestar.config.RateLimitProperties;
import com.br.spbemestar.config.RedisRateLimitStore;
import com.br.spbemestar.observability.RecentErrors;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SharedStateTests {

    @Autowired StringRedisTemplate redis;
    @Autowired JsonMapper json;

    @Test
    void twoInstancesShareTheSameLimit() throws Exception {
        RateLimitProperties properties = limit(3, Duration.ofHours(1));
        RateLimitFilter first = instance(properties, redis);
        RateLimitFilter second = instance(properties, redis);
        String ip = randomIp();

        assertThat(call(first, ip).getStatus()).isEqualTo(200);
        assertThat(call(second, ip).getStatus()).isEqualTo(200);
        assertThat(call(first, ip).getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        MockHttpServletResponse blocked = call(second, ip);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1100L, 1201L);
        assertThat(call(first, randomIp()).getStatus()).isEqualTo(200);
    }

    @Test
    void withoutRedisEachInstanceLimitsOnItsOwn() throws Exception {
        LettuceConnectionFactory down = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build());
        down.afterPropertiesSet();
        down.start();
        try {
            RateLimitFilter offline = instance(limit(1, Duration.ofHours(1)), new StringRedisTemplate(down));
            String ip = randomIp();

            assertThat(call(offline, ip).getStatus()).isEqualTo(200);
            assertThat(call(offline, ip).getStatus()).isEqualTo(429);
        } finally {
            down.destroy();
        }
    }

    @Test
    void recentErrorsFromEveryInstanceShowUpOnAnyOfThem() {
        redis.delete("spbemestar:recent-errors");
        RecentErrors first = new RecentErrors(Clock.systemUTC(), redis, new RedisAvailability(Clock.systemUTC()),
                json);
        RecentErrors second = new RecentErrors(Clock.systemUTC(), redis, new RedisAvailability(Clock.systemUTC()),
                json);

        first.record("GET", "/api/v1/a", new IllegalStateException("na primeira"), "trace-1");
        second.record("POST", "/api/v1/b", new IllegalArgumentException("na segunda"), "trace-2");

        assertThat(first.latest(10)).extracting(RecentErrors.Entry::path).containsExactly("/api/v1/b", "/api/v1/a");
        assertThat(second.latest(1)).extracting(RecentErrors.Entry::traceId).containsExactly("trace-2");
    }

    private RateLimitFilter instance(RateLimitProperties properties, StringRedisTemplate template) {
        Clock clock = Clock.systemUTC();
        RedisRateLimitStore store = new RedisRateLimitStore(template, new RedisAvailability(clock),
                RateLimitFilter.localStore(properties, clock));
        return new RateLimitFilter(properties, store, json, new SimpleMeterRegistry());
    }

    private static RateLimitProperties limit(int capacity, Duration period) {
        return new RateLimitProperties(true, 1000,
                List.of(new RateLimitProperties.Rule("api", null, "/api/**", capacity, period)));
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static String randomIp() {
        UUID id = UUID.randomUUID();
        return "10.%d.%d.%d".formatted(Math.floorMod(id.hashCode(), 250), Math.floorMod(id.hashCode() >> 8, 250),
                Math.floorMod(id.hashCode() >> 16, 250));
    }
}
