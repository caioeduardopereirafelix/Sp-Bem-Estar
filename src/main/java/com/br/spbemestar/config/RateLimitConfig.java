package com.br.spbemestar.config;

import com.br.spbemestar.redis.RedisAvailability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    @ConditionalOnBooleanProperty(name = "app.rate-limit.enabled", matchIfMissing = true)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties, Clock clock,
                                                            JsonMapper json, MeterRegistry meters,
                                                            StringRedisTemplate redis,
                                                            RedisAvailability availability) {
        RateLimitStore store = new RedisRateLimitStore(redis, availability,
                RateLimitFilter.localStore(properties, clock));
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, store, json, meters));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }
}
