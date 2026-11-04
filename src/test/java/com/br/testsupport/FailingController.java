package com.br.testsupport;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FailingController {

    @GetMapping("/api/v1/categories/falha-de-teste")
    String fail() {
        throw new IllegalStateException("falhou ao conectar com senha-do-banco");
    }
}
