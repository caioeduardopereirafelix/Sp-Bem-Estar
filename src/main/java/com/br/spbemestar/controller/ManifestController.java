package com.br.spbemestar.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * O manifesto do app instalável (PWA). Vai por aqui, e não como arquivo estático, porque o navegador só o aceita
 * como {@code application/manifest+json}, e nem o Tomcat nem o Spring conhecem a extensão {@code .webmanifest}.
 */
@RestController
public class ManifestController {

    private static final MediaType MANIFEST = MediaType.valueOf("application/manifest+json");
    private static final Resource FILE = new ClassPathResource("static/manifest.webmanifest");

    @GetMapping("/manifest.webmanifest")
    public ResponseEntity<Resource> manifest() {
        return ResponseEntity.ok()
                .contentType(MANIFEST)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(FILE);
    }
}
