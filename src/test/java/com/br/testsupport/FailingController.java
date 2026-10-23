package com.br.testsupport;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota que só existe nos testes, para simular um erro que ninguém previu. Fica fora de
 * {@code com.br.spbemestar} para a varredura de componentes não registrá-la em todo contexto de teste.
 */
@RestController
public class FailingController {

    @GetMapping("/api/v1/categories/falha-de-teste")
    String fail() {
        throw new IllegalStateException("falhou ao conectar com senha-do-banco");
    }
}
