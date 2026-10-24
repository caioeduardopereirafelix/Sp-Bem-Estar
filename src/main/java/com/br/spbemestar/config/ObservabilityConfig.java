package com.br.spbemestar.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityConfig {

    // logo depois do filtro que abre o span da requisição (ServerHttpObservationFilter, HIGHEST_PRECEDENCE + 1)
    @Bean
    FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter(TraceContext traceContext) {
        FilterRegistrationBean<TraceIdResponseFilter> registration =
                new FilterRegistrationBean<>(new TraceIdResponseFilter(traceContext));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
