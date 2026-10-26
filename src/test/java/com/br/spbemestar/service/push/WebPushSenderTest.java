package com.br.spbemestar.service.push;

import com.br.spbemestar.config.PushProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushSenderTest {

    private static final TestVapidKeys KEYS = TestVapidKeys.generate();
    private static final String PUBLIC = KEYS.publicKey();
    private static final String PRIVATE = KEYS.privateKey();

    private final WebPushSender sender = sender(new PushProperties(PUBLIC, PRIVATE, "mailto:a@b.gov.br",
            List.of("fcm.googleapis.com", "web.push.apple.com", "*.notify.windows.com"), 5, Duration.ofHours(1), false));

    @Test
    void acceptsOnlyKnownPushServicesOverHttps() {
        assertThat(sender.allowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(sender.allowedEndpoint("https://web.push.apple.com/QGx")).isTrue();
        assertThat(sender.allowedEndpoint("https://wns2-bl2p.notify.windows.com/w/?token=x")).isTrue();

        assertThat(sender.allowedEndpoint("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(sender.allowedEndpoint("https://fcm.googleapis.com:8443/fcm/send/abc")).isFalse();
        assertThat(sender.allowedEndpoint("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(sender.allowedEndpoint("https://notify.windows.com.evil.example/x")).isFalse();
        assertThat(sender.allowedEndpoint("https://evil@fcm.googleapis.com/x")).isFalse();
        assertThat(sender.allowedEndpoint("https://169.254.169.254/latest")).isFalse();
        assertThat(sender.allowedEndpoint("não é uma url")).isFalse();
    }

    @Test
    void disabledWithoutKeys() {
        WebPushSender off = sender(new PushProperties(null, null, null, List.of(), 5, Duration.ofHours(1), false));

        assertThat(off.enabled()).isFalse();
        assertThat(off.publicKey()).isNull();
    }

    @Test
    void refusesToStartWithMismatchedKeysOrNoContact() {
        String otherPublic = TestVapidKeys.generate().publicKey();

        assertThatThrownBy(() -> sender(new PushProperties(otherPublic, PRIVATE, "mailto:a@b.gov.br", List.of(), 5,
                Duration.ofHours(1), false))).isInstanceOf(IllegalStateException.class).hasMessageContaining("VAPID");
        assertThatThrownBy(() -> sender(new PushProperties(PUBLIC, PRIVATE, "", List.of(), 5, Duration.ofHours(1), false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PUSH_VAPID_SUBJECT");
    }

    private static WebPushSender sender(PushProperties properties) {
        return new WebPushSender(properties, Clock.systemUTC(), new SimpleMeterRegistry());
    }
}
