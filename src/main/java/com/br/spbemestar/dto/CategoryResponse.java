package com.br.spbemestar.dto;

import com.br.spbemestar.model.Category;

public record CategoryResponse(String code, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCode(), category.getName());
    }
}
