package com.br.spbemestar.service.push;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.IssueStatusService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Do status alterado pelo operador até o navegador: outbox, RabbitMQ, listener, criptografia e VAPID. Um
 * servidor HTTP local faz o papel do serviço de push e este teste, o do navegador, que decifra a mensagem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PushNotificationTests {

    static final TestVapidKeys VAPID = TestVapidKeys.generate();
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    record Received(String path, Map<String, String> headers, byte[] body) {
    }

    static HttpServer pushService;
    static final BlockingQueue<Received> received = new LinkedBlockingQueue<>();

    @BeforeAll
    static void startPushService() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext("/", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            received.add(new Received(exchange.getRequestURI().getPath(), headers,
                    exchange.getRequestBody().readAllBytes()));
            // "/gone/...": o navegador cancelou a inscrição
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/gone/") ? 410 : 201, -1);
            exchange.close();
        });
        pushService.start();
    }

    @AfterAll
    static void stopPushService() {
        pushService.stop(0);
    }

    @DynamicPropertySource
    static void push(DynamicPropertyRegistry registry) {
        registry.add("app.push.vapid-public-key", VAPID::publicKey);
        registry.add("app.push.vapid-private-key", VAPID::privateKey);
        registry.add("app.push.subject", () -> "mailto:testes@spbemestar.local");
        registry.add("app.push.allowed-hosts", () -> "localhost");
        registry.add("app.push.allow-insecure-endpoints", () -> "true");
    }

    @Autowired MockMvcTester mvc;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired ReporterJwt reporterJwt;
    @Autowired ReportService reportService;
    @Autowired IssueStatusService statusService;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @BeforeEach
    void clear() {
        received.clear();
    }

    @Test
    void publishesTheVapidKey() {
        assertThat(mvc.get().uri("/api/v1/reporters/push/config").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.publicKey").isEqualTo(VAPID.publicKey());
    }

    @Test
    void statusChangeReachesTheReporterBrowserEncrypted() throws Exception {
        Browser browser = new Browser("/push/" + UUID.randomUUID());
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        assertThat(browser.subscribe(token)).hasStatus(HttpStatus.NO_CONTENT);
        Long issueId = reportService.create(subject(token), "POTHOLE", -26.10, -48.10, TestPhotos.jpeg()).issueId();
        // outro cidadão inscrito, sem relato neste chamado
        Browser stranger = new Browser("/push/" + UUID.randomUUID());
        stranger.subscribe(reporterTokens.issueFor(UUID.randomUUID()).accessToken());

        statusService.change(issueId, IssueStatus.IN_PROGRESS, operatorId());

        Received push = received.poll(20, TimeUnit.SECONDS);
        assertThat(push).as("notificação entregue ao serviço de push").isNotNull();
        assertThat(push.path()).isEqualTo(browser.path);
        assertThat(push.headers()).containsEntry("content-encoding", "aes128gcm").containsKey("ttl");

        JsonNode message = json.readTree(browser.decrypt(push.body()));
        assertThat(message.path("title").asString()).isEqualTo("Atendimento em andamento");
        assertThat(message.path("body").asString()).contains("#" + issueId);
        assertThat(message.path("issueId").asLong()).isEqualTo(issueId);

        // RFC 8292: JWT assinado com a chave VAPID, para a origem do serviço de push
        String authorization = push.headers().get("authorization");
        assertThat(authorization).startsWith("vapid t=").contains("k=" + VAPID.publicKey());
        SignedJWT jwt = SignedJWT.parse(authorization.substring("vapid t=".length(), authorization.indexOf(',')));
        assertThat(jwt.verify(new ECDSAVerifier(WebPushEncryption.publicKey(Base64.getUrlDecoder().decode(VAPID.publicKey())))))
                .isTrue();
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("http://localhost:" + pushService.getAddress().getPort());
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("mailto:testes@spbemestar.local");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isAfter(Instant.now())
                .isBefore(Instant.now().plus(Duration.ofHours(24)));

        assertThat(received.poll(2, TimeUnit.SECONDS)).as("quem não relatou não recebe").isNull();
    }

    @Test
    void cancelledSubscriptionIsRemoved() throws Exception {
        Browser browser = new Browser("/gone/" + UUID.randomUUID());
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        browser.subscribe(token);
        Long issueId = reportService.create(subject(token), "POTHOLE", -26.20, -48.20, TestPhotos.jpeg()).issueId();

        statusService.change(issueId, IssueStatus.RESOLVED, operatorId());

        assertThat(received.poll(20, TimeUnit.SECONDS)).isNotNull();
        long deadline = System.currentTimeMillis() + 5000;
        while (count(browser.endpoint()) > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(count(browser.endpoint())).isZero();
    }

    @Test
    void sameBrowserSubscribingAgainMovesToTheCurrentIdentity() throws Exception {
        Browser browser = new Browser("/push/" + UUID.randomUUID());
        browser.subscribe(reporterTokens.issueFor(UUID.randomUUID()).accessToken());
        String newIdentity = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        browser.subscribe(newIdentity);

        assertThat(count(browser.endpoint())).isEqualTo(1);
        Long issueId = reportService.create(subject(newIdentity), "TRASH", -26.30, -48.30, TestPhotos.jpeg()).issueId();
        statusService.change(issueId, IssueStatus.CONFIRMED, operatorId());

        Received push = received.poll(20, TimeUnit.SECONDS);
        assertThat(push).isNotNull();
        assertThat(json.readTree(browser.decrypt(push.body())).path("title").asString()).isEqualTo("Problema confirmado");
    }

    @Test
    void rejectsEndpointsOutsideThePushServices() throws Exception {
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        Browser browser = new Browser("/push/x");

        // o endpoint vem do navegador: sem a lista, a API faria requisições para a rede interna
        assertThat(subscribe(token, "https://169.254.169.254/latest/meta-data", browser.p256dh(), browser.auth()))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(subscribe(token, "http://user@localhost:" + pushService.getAddress().getPort() + "/x",
                browser.p256dh(), browser.auth())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(subscribe(token, browser.endpoint(), "não-é-chave", browser.auth())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(subscribe(token, browser.endpoint(), browser.p256dh(), B64.encodeToString(new byte[8])))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(subscribe(null, browser.endpoint(), browser.p256dh(), browser.auth()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unsubscribeRemovesOnlyOwnSubscription() throws Exception {
        Browser browser = new Browser("/push/" + UUID.randomUUID());
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        browser.subscribe(token);

        assertThat(unsubscribe(reporterTokens.issueFor(UUID.randomUUID()).accessToken(), browser)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count(browser.endpoint())).isEqualTo(1);
        assertThat(unsubscribe(token, browser)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count(browser.endpoint())).isZero();
    }

    private MvcTestResult unsubscribe(String token, Browser browser) {
        return mvc.delete().uri("/api/v1/reporters/me/push-subscription")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(browser.endpoint(), browser.p256dh(), browser.auth()))
                .exchange();
    }

    private MvcTestResult subscribe(String token, String endpoint, String p256dh, String authSecret) {
        var request = mvc.put().uri("/api/v1/reporters/me/push-subscription")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint, p256dh, authSecret));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange();
    }

    private static String body(String endpoint, String p256dh, String authSecret) {
        return """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}
                """.formatted(endpoint, p256dh, authSecret);
    }

    private long count(String endpoint) {
        return jdbc.queryForObject("SELECT count(*) FROM push_subscription WHERE endpoint = ?", Long.class, endpoint);
    }

    private UUID subject(String token) {
        return UUID.fromString(reporterJwt.decoder().decode(token).getSubject());
    }

    private Long operatorId() {
        TestOperators.bearer(auth, users);
        return users.findByEmail(TestOperators.EMAIL).orElseThrow().getId();
    }

    /** O navegador: gera as chaves da inscrição e decifra a mensagem como manda a RFC 8291. */
    class Browser {

        final String path;
        final KeyPair keys = WebPushEncryption.newKeyPair();
        final byte[] authSecret = new byte[16];

        Browser(String path) {
            this.path = path;
            new SecureRandom().nextBytes(authSecret);
        }

        String endpoint() {
            return "http://localhost:" + pushService.getAddress().getPort() + path;
        }

        String p256dh() {
            return B64.encodeToString(WebPushEncryption.encodePoint((ECPublicKey) keys.getPublic()));
        }

        String auth() {
            return B64.encodeToString(authSecret);
        }

        MvcTestResult subscribe(String token) {
            return PushNotificationTests.this.subscribe(token, endpoint(), p256dh(), auth());
        }

        byte[] decrypt(byte[] message) throws Exception {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            byte[] salt = new byte[16];
            buffer.get(salt);
            buffer.getInt(); // tamanho do registro
            byte[] asPublic = new byte[buffer.get()];
            buffer.get(asPublic);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(keys.getPrivate());
            agreement.doPhase(WebPushEncryption.publicKey(asPublic), true);
            byte[] uaPublic = WebPushEncryption.encodePoint((ECPublicKey) keys.getPublic());
            byte[] ikm = hkdf(authSecret, agreement.generateSecret(),
                    concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic), 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            assertThat(plaintext[plaintext.length - 1]).as("delimitador do último registro").isEqualTo((byte) 2);
            return Arrays.copyOf(plaintext, plaintext.length - 1);
        }
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{1})), length);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
