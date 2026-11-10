package com.br.spbemestar.service.cache;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.dto.DashboardResponse;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.redis.RedisAvailability;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.DashboardService;
import com.br.spbemestar.service.NeighborhoodImportService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.cache.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JsonCacheTests {

    @Autowired JsonCache cache;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry meters;
    @Autowired MockMvcTester mvc;
    @Autowired DashboardService dashboard;
    @Autowired NeighborhoodImportService neighborhoods;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired JsonMapper json;

    @Test
    void theSecondReadComesFromRedisUntilEvicted() {
        String cacheName = "teste-" + UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get(cacheName, "chave", Duration.ofMinutes(1), String.class, () -> "valor-" + loads.incrementAndGet());
        String second = cache.get(cacheName, "chave", Duration.ofMinutes(1), String.class, () -> "valor-" + loads.incrementAndGet());

        assertThat(first).isEqualTo("valor-1");
        assertThat(second).isEqualTo("valor-1");
        assertThat(loads).hasValue(1);
        assertThat(redis.getExpire(JsonCache.key(cacheName, "chave"))).isBetween(1L, 60L);

        cache.evict(cacheName);

        assertThat(cache.get(cacheName, "chave", Duration.ofMinutes(1), String.class, () -> "valor-" + loads.incrementAndGet()))
                .isEqualTo("valor-2");
    }

    @Test
    void districtGeometriesAreCachedAndAnImportClearsThem() {
        neighborhoods.importGeoJson(new ByteArrayInputStream(square("Vila Cache", -47.90).getBytes(StandardCharsets.UTF_8)),
                new NeighborhoodImportService.ImportOptions("Cidade do Cache", "name", "population", 4326));
        double misses = count("district-geometries", "miss");
        double hits = count("district-geometries", "hit");

        assertThat(mvc.get().uri("/api/v1/neighborhoods?city=Cidade do Cache").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.features[0].properties.name").isEqualTo("Vila Cache");
        assertThat(mvc.get().uri("/api/v1/neighborhoods?city=Cidade do Cache").exchange()).hasStatusOk();

        assertThat(count("district-geometries", "miss")).isEqualTo(misses + 1);
        assertThat(count("district-geometries", "hit")).isEqualTo(hits + 1);

        neighborhoods.importGeoJson(new ByteArrayInputStream(square("Vila Nova", -47.80).getBytes(StandardCharsets.UTF_8)),
                new NeighborhoodImportService.ImportOptions("Cidade do Cache", "name", "population", 4326));

        assertThat(mvc.get().uri("/api/v1/neighborhoods?city=Cidade do Cache").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.features[*].properties.name").asArray().contains("Vila Nova");
        assertThat(count("district-geometries", "miss")).isEqualTo(misses + 2);
    }

    @Test
    void theCeoDashboardIsReusedForAFewSeconds() {
        DashboardResponse first = dashboard.dashboard();
        DashboardResponse second = dashboard.dashboard();

        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.product().reportsTotal()).isEqualTo(first.product().reportsTotal());
    }

    @Test
    void open311RequestsAreCachedPerQuery() {
        double hits = count("open311-requests", "hit");

        assertThat(mvc.get().uri("/open311/v2/requests.json?status=open").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/open311/v2/requests.json?status=open").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/open311/v2/requests.json?status=closed").exchange()).hasStatusOk();

        assertThat(count("open311-requests", "hit")).isEqualTo(hits + 1);
    }

    @Test
    void withoutRedisTheApiAnswersFromTheDatabase() {
        LettuceConnectionFactory down = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build());
        down.afterPropertiesSet();
        down.start();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JsonCache offline = new JsonCache(new StringRedisTemplate(down), json, registry,
                new RedisAvailability(Clock.systemUTC()), true);
        try {
            AtomicInteger loads = new AtomicInteger();
            assertThat(offline.get("teste", "a", Duration.ofMinutes(1), Integer.class, loads::incrementAndGet)).isEqualTo(1);
            long start = System.nanoTime();
            assertThat(offline.get("teste", "a", Duration.ofMinutes(1), Integer.class, loads::incrementAndGet)).isEqualTo(2);

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
            assertThat(registry.counter("spbemestar.cache", "cache", "teste", "result", "error").count()).isEqualTo(1);
            assertThat(registry.counter("spbemestar.cache", "cache", "teste", "result", "bypass").count()).isEqualTo(1);
            assertThat(offline.ping()).isFalse();
        } finally {
            down.destroy();
        }
    }

    @Test
    void theCeoDashboardShowsTheCacheHealth() {
        String ceo = "ceo-cache@teste.com.br";
        var user = users.findByEmail(ceo).orElseGet(() -> auth.createUser(ceo, "CEO", UserRole.CEO));
        TestOperators.bearer(auth, users);

        assertThat(mvc.get().uri("/api/v1/ceo/dashboard")
                .header("Authorization", "Bearer " + auth.issueToken(user).accessToken()).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.health.components[?(@.name == 'cache')].status")
                .asArray().containsExactly("UP");
    }

    private double count(String cacheName, String result) {
        Counter counter = meters.find("spbemestar.cache").tag("cache", cacheName).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String square(String name, double lon) {
        return """
                {"type": "FeatureCollection", "features": [{"type": "Feature",
                  "properties": {"name": "%s", "population": "1000"},
                  "geometry": {"type": "Polygon", "coordinates": [[[%s, -15.8], [%s, -15.8], [%s, -15.79], [%s, -15.79], [%s, -15.8]]]}}]}
                """.formatted(name, lon, lon + 0.01, lon + 0.01, lon, lon);
    }
}
