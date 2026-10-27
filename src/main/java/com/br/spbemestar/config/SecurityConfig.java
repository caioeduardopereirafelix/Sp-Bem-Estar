package com.br.spbemestar.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import com.br.spbemestar.repository.AppUserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * O cidadão usa a API sem login: consultar o mapa e enviar relatos continua público. Só o órgão
 * público se autentica, com um token JWT obtido em {@code POST /api/v1/auth/login}. Rotas novas
 * nascem protegidas: o que é público está listado aqui.
 */
@Configuration
@EnableConfigurationProperties({SecurityProperties.class, MetricsProperties.class, ReporterProperties.class,
        PushProperties.class})
public class SecurityConfig {

    public static final String ROLES_CLAIM = "roles";
    static final String METRICS_PATH = "/actuator/prometheus";
    // Rotas do cidadão: enviar relato com o token dele, emitir ou renovar esse token, ver os próprios relatos
    // e inscrever o navegador nas notificações
    private static final RequestMatcher REPORTER_ROUTES = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/reports"),
            PathPatternRequestMatcher.pathPattern("/api/v1/reporters"),
            PathPatternRequestMatcher.pathPattern("/api/v1/reporters/**"));
    private static final RequestMatcher SWAGGER_UI = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern("/swagger-ui.html"),
            PathPatternRequestMatcher.pathPattern("/swagger-ui/**"));

    /**
     * O mapa só carrega scripts, estilos e imagens da própria API, do Leaflet e do MapLibre no cdnjs, do mapa
     * vetorial do OpenFreeMap e dos tiles do OpenStreetMap (a reserva sem WebGL); um script injetado num relato
     * não roda, nem manda dados para outro domínio.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdnjs.cloudflare.com",
            "style-src 'self' https://cdnjs.cloudflare.com",
            // blob: é a prévia da foto escolhida no relato, que ainda está só no aparelho
            "img-src 'self' data: blob: https://cdnjs.cloudflare.com https://tile.openstreetmap.org",
            // estilo, ruas, ícones e fontes do mapa vetorial
            "connect-src 'self' https://tiles.openfreemap.org",
            // o service worker das notificações, e o worker em que o MapLibre desenha o mapa (criado a partir do
            // próprio código, como blob:)
            "worker-src 'self' blob:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** O mapa pede a localização do usuário; nada mais da página usa recursos do aparelho. */
    static final String PERMISSIONS_POLICY = "geolocation=(self), camera=(), microphone=(), payment=(), usb=()";

    /** O Swagger UI traz um {@code <style>} inline; só nas páginas dele os estilos inline são aceitos. */
    static final String SWAGGER_CONTENT_SECURITY_POLICY = CONTENT_SECURITY_POLICY
            .replace("style-src 'self'", "style-src 'self' 'unsafe-inline'");

    /**
     * As métricas revelam rotas, volumes e erros: só o Prometheus as lê, com usuário e senha próprios
     * (o token do operador vence em horas, o que não serve para um coletor).
     */
    @Bean
    @Order(1)
    SecurityFilterChain metricsFilterChain(HttpSecurity http, MetricsProperties metrics,
                                           PasswordEncoder passwordEncoder, JsonMapper json) throws Exception {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (metrics.enabled()) {
            users.createUser(User.withUsername(metrics.username())
                    .password(passwordEncoder.encode(metrics.password()))
                    .roles("METRICS")
                    .build());
        }
        http
                .securityMatcher(METRICS_PATH)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .userDetailsService(users)
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, e) -> {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"metrics\"");
                    writeProblem(response, json, HttpStatus.UNAUTHORIZED, "Não autenticado",
                            "As métricas exigem o usuário e a senha do Prometheus.");
                }))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("METRICS"));
        return http.build();
    }

    /**
     * O cidadão envia relatos com o JWT emitido em {@code POST /api/v1/reporters}. Esta cadeia só aceita esse
     * token, com chave e {@code aud} próprios; o do operador não serve aqui, nem o do cidadão nas outras rotas.
     */
    @Bean
    @Order(2)
    SecurityFilterChain reporterFilterChain(HttpSecurity http, ReporterJwt reporterJwt, JsonMapper json)
            throws Exception {
        JwtAuthenticationConverter reporterConverter = new JwtAuthenticationConverter();
        reporterConverter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_REPORTER")));

        http
                .securityMatcher(REPORTER_ROUTES)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports").hasRole("REPORTER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reporters", "/api/v1/reporters/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reporters/push/config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reporters/me/reports").hasRole("REPORTER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/reporters/me/push-subscription").hasRole("REPORTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reporters/me/push-subscription").hasRole("REPORTER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(reporterJwt.decoder()).jwtAuthenticationConverter(reporterConverter))
                        .authenticationEntryPoint((request, response, e) -> writeProblem(response, json,
                                HttpStatus.UNAUTHORIZED, "Não autenticado",
                                "Envie o token de cidadão, obtido em POST /api/v1/reporters, no header Authorization."))
                        .accessDeniedHandler((request, response, e) -> writeProblem(response, json,
                                HttpStatus.FORBIDDEN, "Acesso negado", "Operação não permitida.")));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authorities);

        http
                // API sem cookies de sessão: o token vai no header, então não há CSRF a proteger
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .headers(headers -> headers
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(SWAGGER_UI,
                                new ContentSecurityPolicyHeaderWriter(SWAGGER_CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(SWAGGER_UI),
                                new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/issues/*/status").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/issues/*/resolution").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/*/block-reporter").hasRole("OPERATOR")
                        // antes do GET /api/v1/** público: fila e moderação são só do operador
                        .requestMatchers(HttpMethod.GET, "/api/v1/issues/queue").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/issues/export").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/moderation/**").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/operators", "/api/v1/operators/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/auth/password").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        // mapa web, documentação e monitoramento
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/privacidade.html", "/termos.html", "/*.js", "/*.css",
                                "/favicon.ico", "/fonts/**", "/vendor/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint((request, response, e) -> writeProblem(response, json,
                                HttpStatus.UNAUTHORIZED, "Não autenticado",
                                "Faça login como operador e envie o token no header Authorization."))
                        .accessDeniedHandler((request, response, e) -> writeProblem(response, json,
                                HttpStatus.FORBIDDEN, "Acesso negado",
                                "Seu usuário não tem permissão para esta operação.")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, ObjectProvider<AppUserRepository> users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(properties.issuer()),
                new OperatorTokenValidator(users)));
        return decoder;
    }

    static SecretKey secretKey(SecurityProperties properties) {
        String secret = properties.jwtSecret();
        // HS256 exige ao menos 256 bits; um segredo curto seria quebrado por força bruta
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.security.jwt-secret (JWT_SECRET) precisa ter ao menos 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static void writeProblem(HttpServletResponse response, JsonMapper json,
                                     HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED && !response.containsHeader("WWW-Authenticate")) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        json.writeValue(response.getOutputStream(), problem);
    }
}
