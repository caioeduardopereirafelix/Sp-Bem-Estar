package com.br.spbemestar.classifier.service;

public interface PhotoClassifier {

    Classification classify(byte[] photo);
}
