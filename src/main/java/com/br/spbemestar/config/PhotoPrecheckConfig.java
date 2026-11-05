package com.br.spbemestar.config;

import com.br.spbemestar.service.HttpPhotoPrecheck;
import com.br.spbemestar.service.PhotoPrecheck;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PhotoPrecheckProperties.class)
public class PhotoPrecheckConfig {

    @Bean
    PhotoPrecheck photoPrecheck(PhotoPrecheckProperties properties, MeterRegistry meters,
                                ObjectProvider<ObservationRegistry> observations) {
        RestClient.Builder client = RestClients.withTimeout(properties.timeout());
        observations.ifAvailable(client::observationRegistry);
        return new HttpPhotoPrecheck(properties, client.build(), meters);
    }
}
