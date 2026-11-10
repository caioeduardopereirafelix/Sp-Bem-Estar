package com.br.spbemestar.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class StaticCacheConfig implements WebMvcConfigurer {

    static final Duration FONT_CACHE = Duration.ofDays(30);
    static final String[] STATIC_FILES = {"*.html", "*.js", "*.css", "*.webmanifest", "*.png", "*.ico", "*.woff2"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(FONT_CACHE).cachePublic())
                .setUseLastModified(false);
    }

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> staticEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns(STATIC_FILES);
        return registration;
    }
}
