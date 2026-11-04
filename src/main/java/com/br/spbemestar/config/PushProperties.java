package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.push")
public record PushProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject,
        @DefaultValue({"fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com",
                "*.notify.windows.com"}) List<String> allowedHosts,
        @DefaultValue("5") int maxSubscriptionsPerReporter,
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("false") boolean allowInsecureEndpoints
) {

    public boolean enabled() {
        return StringUtils.hasText(vapidPublicKey) && StringUtils.hasText(vapidPrivateKey);
    }
}
