package com.br.spbemestar.config;

import com.br.spbemestar.dto.open311.Open311Error;
import io.micrometer.core.instrument.MeterRegistry;
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

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final List<CompiledRule> rules;
    private final RateLimitStore store;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public RateLimitFilter(RateLimitProperties properties, Clock clock, JsonMapper json, MeterRegistry meters) {
        this(properties, localStore(properties, clock), json, meters);
    }

    public RateLimitFilter(RateLimitProperties properties, RateLimitStore store, JsonMapper json,
                           MeterRegistry meters) {
        this.rules = compile(properties);
        this.store = store;
        this.json = json;
        this.meters = meters;
        rules.forEach(rule -> meters.counter("spbemestar.ratelimit.rejected", "rule", rule.name()));
    }

    public static LocalRateLimitStore localStore(RateLimitProperties properties, Clock clock) {
        Duration longestPeriod = compile(properties).stream().map(CompiledRule::period)
                .max(Comparator.naturalOrder()).orElse(Duration.ofMinutes(1));
        return new LocalRateLimitStore(properties.maxTrackedClients(), longestPeriod, clock);
    }

    private static List<CompiledRule> compile(RateLimitProperties properties) {
        return properties.rules().stream().map(CompiledRule::of).toList();
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
        RateLimitStore.Probe probe = store.consume(rule.name() + '|' + client, rule.capacity(), rule.period());
        response.setHeader(LIMIT_HEADER, String.valueOf(rule.capacity()));
        response.setHeader(REMAINING_HEADER, String.valueOf(probe.remaining()));
        if (probe.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, (probe.nanosToWait() + 999_999_999) / 1_000_000_000);
        meters.counter("spbemestar.ratelimit.rejected", "rule", rule.name()).increment();
        log.debug("Rate limit '{}' atingido por {} em {} {}", rule.name(), client,
                request.getMethod(), request.getRequestURI());
        String detail = "Você fez requisições demais em pouco tempo. Tente de novo em %d s.".formatted(retryAfter);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        if (request.getRequestURI().startsWith(request.getContextPath() + "/open311/")) {
            if (request.getHeader(HttpHeaders.ORIGIN) != null) {
                response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), List.of(new Open311Error(429, detail)));
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail);
        problem.setTitle("Muitas requisições");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    static String clientKey(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
            return String.valueOf(remoteAddr);
        }
        int scope = remoteAddr.indexOf('%');
        String literal = scope < 0 ? remoteAddr : remoteAddr.substring(0, scope);
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (!(address instanceof Inet6Address)) {
                return address.getHostAddress();
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
    }
}
