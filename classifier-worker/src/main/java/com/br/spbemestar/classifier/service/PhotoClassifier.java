package com.br.spbemestar.classifier.service;

public interface PhotoClassifier {

    /**
     * @throws com.br.spbemestar.classifier.exception.InvalidPhotoException se os bytes não forem uma imagem legível
     */
    Classification classify(byte[] photo);
}
