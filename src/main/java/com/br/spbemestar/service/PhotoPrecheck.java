package com.br.spbemestar.service;

import java.util.Optional;

public interface PhotoPrecheck {

    String NOT_A_PROBLEM = "OTHER";

    Optional<Prediction> classify(byte[] photo, String contentType);

    record Prediction(String category, double confidence) {
    }
}
