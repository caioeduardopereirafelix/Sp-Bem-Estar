package com.br.spbemestar.controller;

import com.br.spbemestar.dto.CategoryResponse;
import com.br.spbemestar.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categories;

    @GetMapping
    public List<CategoryResponse> list() {
        return categories.findByActiveTrueOrderByName().stream().map(CategoryResponse::from).toList();
    }
}
