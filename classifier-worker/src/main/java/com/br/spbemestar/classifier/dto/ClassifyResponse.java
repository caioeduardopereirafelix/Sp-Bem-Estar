package com.br.spbemestar.classifier.dto;

/**
 * @param label        categoria prevista ({@code OTHER}: nenhum problema de rua)
 * @param confidence   confiança do modelo, de 0 a 1
 * @param modelVersion versão do modelo que respondeu
 */
public record ClassifyResponse(String label, double confidence, String modelVersion) {
}
