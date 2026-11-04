package com.br.spbemestar.exception;

import lombok.Getter;

@Getter
public class PhotoCategoryMismatchException extends RuntimeException {

    private final String chosenCategory;
    private final String predictedCategory;
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
