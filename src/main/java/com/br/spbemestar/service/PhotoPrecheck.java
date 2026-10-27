package com.br.spbemestar.service;

import java.util.Optional;

/** Pergunta ao classificador, na hora do envio, o que ele vê na foto. */
public interface PhotoPrecheck {

    /** Categoria do modelo para "nenhum problema de rua". */
    String NOT_A_PROBLEM = "OTHER";

    /**
     * @return vazio quando a conferência está desligada ou o classificador não respondeu: quem chama segue sem ela
     */
    Optional<Prediction> classify(byte[] photo, String contentType);

    record Prediction(String category, double confidence) {
    }
}
