package com.br.spbemestar.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvDollarCheckTest {

    @Test
    void pointsOutOnlyTheDotEnvValuesThatHaveADollarSign() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'file [.env]' via location 'optional:file:.env[.properties]'",
                Map.of("POSTGRES_PASSWORD", withDollar(), "MONGO_PASSWORD", withDollar(), "REDIS_PORT", "6379")));
        environment.getPropertySources().addLast(new MapPropertySource("outro", Map.of("OUTRA", withDollar())));

        assertThat(DotEnvDollarCheck.variablesWithDollar(environment))
                .containsExactly("MONGO_PASSWORD", "POSTGRES_PASSWORD");
    }

    private static String withDollar() {
        return UUID.randomUUID().toString().replace("-", "$");
    }

    @Test
    void staysQuietWithoutADotEnv() {
        assertThat(DotEnvDollarCheck.variablesWithDollar(new StandardEnvironment())).isEmpty();
    }
}
