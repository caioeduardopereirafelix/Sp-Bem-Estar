package com.br.spbemestar;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgisContainer() {
        return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmqContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        // 7.0, não 8.x: a 8.x não sobe em kernels Linux 6.19 a 7.0.13 (SERVER-121912), como os de parte dos runners
        return new MongoDBContainer(DockerImageName.parse("mongo:7"));
    }
}
