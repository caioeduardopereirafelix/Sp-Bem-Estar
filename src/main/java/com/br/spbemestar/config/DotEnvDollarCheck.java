package com.br.spbemestar.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.Arrays;
import java.util.List;

public class DotEnvDollarCheck implements EnvironmentPostProcessor, Ordered {

    private final Log log;

    public DotEnvDollarCheck(DeferredLogFactory logs) {
        this.log = logs.getLog(DotEnvDollarCheck.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> names = variablesWithDollar(environment);
        if (!names.isEmpty()) {
            log.warn("O .env tem $ em " + String.join(", ", names) + ". O Docker Compose lê $ como início de "
                    + "variável e corta o valor, então os containers recebem um valor diferente do que a API lê "
                    + "do arquivo, e a senha deixa de bater. Troque por um valor só com letras e números.");
        }
    }

    static List<String> variablesWithDollar(ConfigurableEnvironment environment) {
        return environment.getPropertySources().stream()
                .filter(source -> source.getName().contains("[.env]"))
                .filter(EnumerablePropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast)
                .flatMap(source -> Arrays.stream(source.getPropertyNames())
                        .filter(name -> source.getProperty(name) instanceof CharSequence value
                                && value.toString().indexOf('$') >= 0))
                .sorted()
                .toList();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
