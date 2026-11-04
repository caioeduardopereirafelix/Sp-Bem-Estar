package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties({MetricsProperties.class, ClassifyApiProperties.class})
public class ActuatorSecurityConfig {

    @Bean
    UserDetailsService workerUsers(MetricsProperties metrics, ClassifyApiProperties classifyApi) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (metrics.enabled()) {
            users.createUser(User.withUsername(metrics.username())
                    .password(passwordEncoder.encode(metrics.password()))
                    .roles("METRICS")
                    .build());
        }
        if (classifyApi.enabled()) {
            users.createUser(User.withUsername(classifyApi.username())
                    .password(passwordEncoder.encode(classifyApi.password()))
                    .roles("CLASSIFY")
                    .build());
        }
        return users;
    }

    @Bean
    SecurityFilterChain actuatorFilterChain(HttpSecurity http, UserDetailsService users) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .userDetailsService(users)
                .httpBasic(basic -> basic.realmName("metrics"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("METRICS")
                        .requestMatchers(HttpMethod.POST, "/api/classify").hasRole("CLASSIFY")
                        .anyRequest().denyAll());
        return http.build();
    }
}
