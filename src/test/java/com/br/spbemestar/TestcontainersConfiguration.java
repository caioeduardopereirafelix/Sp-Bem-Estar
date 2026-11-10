package com.br.spbemestar;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @Primary
    TestMailbox testMailbox() {
        return new TestMailbox();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgisContainer() {
        return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmqContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.4.0-management"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.2.11-alpine")).withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:7.0.45"));
    }
}
