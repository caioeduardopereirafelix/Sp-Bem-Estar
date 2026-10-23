package com.br.spbemestar.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Limita quantas requisições cada IP faz por regra, com um token bucket por par (regra, IP). Roda
 * antes do Spring Security, para que uma força bruta no login seja barrada sem nem conferir a senha.
 * <p>
 * O IP é o {@code remoteAddr} da conexão. Atrás de um proxy reverso, ele só reflete o cliente se a
 * aplicação confiar no {@code X-Forwarded-For} desse proxy ({@code server.forward-headers-strategy});
 * sem isso, todos os clientes dividiriam o limite do IP do proxy.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final List<CompiledRule> rules;
    private final Cache<String, Bucket> buckets;
    private final TimeMeter time;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public RateLimitFilter(RateLimitProperties properties, Clock clock, JsonMapper json, MeterRegistry meters) {
        this.rules = properties.rules().stream().map(CompiledRule::of).toList();
        Duration longestPeriod = rules.stream().map(CompiledRule::period)
                .max(Comparator.naturalOrder()).orElse(Duration.ofMinutes(1));
        // Um bucket parado por um período inteiro já está cheio de novo: descartá-lo não muda nada
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedClients())
                .expireAfterAccess(longestPeriod)
                .build();
        this.time = new ClockTimeMeter(clock);
        this.json = json;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CompiledRule rule = rules.stream().filter(r -> r.matcher().matches(request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String client = clientKey(request.getRemoteAddr());
        Bucket bucket = buckets.get(rule.name() + '|' + client, key -> rule.newBucket(time));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader(LIMIT_HEADER, String.valueOf(rule.capacity()));
        response.setHeader(REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        // arredonda para cima: voltar no segundo indicado sempre encontra uma ficha
        long retryAfter = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999) / 1_000_000_000);
        // debug, não warn: sob ataque seria uma linha por requisição; o total de 429 já aparece no /actuator/prometheus
        meters.counter("spbemestar.ratelimit.rejected", "rule", rule.name()).increment();
        log.debug("Rate limit '{}' atingido por {} em {} {}", rule.name(), client,
                request.getMethod(), request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Você fez requisições demais em pouco tempo. Tente de novo em %d s.".formatted(retryAfter));
        problem.setTitle("Muitas requisições");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    /**
     * IPv4 conta por endereço. IPv6 conta pelo prefixo /64, que é o que um provedor entrega a um único
     * cliente: contar por endereço deixaria qualquer um trocar de IP a cada requisição.
     */
    static String clientKey(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
            return String.valueOf(remoteAddr);
        }
        int scope = remoteAddr.indexOf('%');
        String literal = scope < 0 ? remoteAddr : remoteAddr.substring(0, scope);
        try {
            // com ':' é sempre um literal IPv6, então não há consulta de DNS
            InetAddress address = InetAddress.getByName(literal);
            if (!(address instanceof Inet6Address)) {
                return address.getHostAddress(); // IPv4 mapeado em IPv6 (::ffff:a.b.c.d)
            }
            byte[] prefix = Arrays.copyOf(Arrays.copyOf(address.getAddress(), 8), 16);
            return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }

    private record CompiledRule(String name, RequestMatcher matcher, int capacity, Duration period) {

        static CompiledRule of(RateLimitProperties.Rule rule) {
            if (!StringUtils.hasText(rule.name()) || !StringUtils.hasText(rule.path())
                    || rule.capacity() < 1 || rule.period() == null || !rule.period().isPositive()) {
                throw new IllegalStateException("Regra de app.rate-limit inválida: " + rule);
            }
            HttpMethod method = StringUtils.hasText(rule.method()) ? HttpMethod.valueOf(rule.method().toUpperCase()) : null;
            return new CompiledRule(rule.name(), PathPatternRequestMatcher.pathPattern(method, rule.path()),
                    rule.capacity(), rule.period());
        }

        Bucket newBucket(TimeMeter time) {
            return Bucket.builder()
                    .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                    .withCustomTimePrecision(time)
                    .build();
        }
    }

    /** Usa o {@link Clock} da aplicação, para os testes poderem avançar o tempo. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            var now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000 + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
