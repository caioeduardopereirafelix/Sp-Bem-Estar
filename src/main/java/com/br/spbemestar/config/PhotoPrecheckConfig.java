package com.br.spbemestar.config;

import com.br.spbemestar.service.HttpPhotoPrecheck;
import com.br.spbemestar.service.PhotoPrecheck;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(PhotoPrecheckProperties.class)
public class PhotoPrecheckConfig {

    /** Tempo curto: quem espera é o cidadão, com o relato na mão; sem resposta, o relato segue sem a conferência. */
    @Bean
    PhotoPrecheck photoPrecheck(PhotoPrecheckProperties properties, MeterRegistry meters,
                                ObjectProvider<ObservationRegistry> observations) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.timeout());
        RestClient.Builder client = RestClient.builder().requestFactory(requests);
        observations.ifAvailable(client::observationRegistry); // o traceparent do relato vai junto para o worker
        return new HttpPhotoPrecheck(properties, client.build(), meters);
    }
}
