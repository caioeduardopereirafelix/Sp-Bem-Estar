package com.br.spbemestar.exception;

import lombok.Getter;

/** O classificador viu outra coisa na foto, com confiança alta: o envio volta para a pessoa escolher. */
@Getter
public class PhotoCategoryMismatchException extends RuntimeException {

    private final String chosenCategory;
    private final String predictedCategory;
    /** Nome do tipo previsto; vazio quando o modelo não viu problema de rua nenhum. */
    private final String predictedCategoryName;
    private final double confidence;

    public PhotoCategoryMismatchException(String chosenCategory, String predictedCategory,
                                          String predictedCategoryName, double confidence) {
        super(predictedCategoryName == null
                ? "A foto não parece mostrar um problema de rua."
                : "A foto parece ser de \"" + predictedCategoryName + "\", e não do tipo escolhido.");
        this.chosenCategory = chosenCategory;
        this.predictedCategory = predictedCategory;
        this.predictedCategoryName = predictedCategoryName;
        this.confidence = confidence;
    }
}
