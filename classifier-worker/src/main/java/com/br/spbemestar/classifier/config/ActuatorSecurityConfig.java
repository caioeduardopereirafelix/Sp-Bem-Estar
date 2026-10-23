package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O worker só fala HTTP para ser monitorado: o health check é público (para o orquestrador saber se ele
 * está de pé) e as métricas exigem o usuário do Prometheus. Qualquer outra rota é recusada.
 */
@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class ActuatorSecurityConfig {

    @Bean
    SecurityFilterChain actuatorFilterChain(HttpSecurity http, MetricsProperties metrics) throws Exception {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (metrics.enabled()) {
            users.createUser(User.withUsername(metrics.username())
                    .password(passwordEncoder.encode(metrics.password()))
                    .roles("METRICS")
                    .build());
        }
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .userDetailsService(users)
                .httpBasic(basic -> basic.realmName("metrics"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("METRICS")
                        .anyRequest().denyAll());
        return http.build();
    }
}
