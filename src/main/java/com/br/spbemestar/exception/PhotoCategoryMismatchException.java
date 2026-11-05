package com.br.spbemestar.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class PhotoCategoryMismatchException extends ApiException {

    private final String chosenCategory;
    private final String predictedCategory;
    private final String predictedCategoryName;
    private final double confidence;

    public PhotoCategoryMismatchException(String chosenCategory, String predictedCategory,
                                          String predictedCategoryName, double confidence) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "A foto não combina com o tipo escolhido",
                predictedCategoryName == null
                        ? "A foto não parece mostrar um problema de rua."
                        : "A foto parece ser de \"" + predictedCategoryName + "\", e não do tipo escolhido.");
        this.chosenCategory = chosenCategory;
        this.predictedCategory = predictedCategory;
        this.predictedCategoryName = predictedCategoryName;
        this.confidence = confidence;
    }

    @Override
    public Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("chosenCategory", chosenCategory);
        properties.put("predictedCategory", predictedCategory);
        properties.put("predictedCategoryName", predictedCategoryName);
        properties.put("confidence", confidence);
        return properties;
    }
}
