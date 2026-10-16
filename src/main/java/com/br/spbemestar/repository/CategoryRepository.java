package com.br.spbemestar.repository;

import com.br.spbemestar.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByActiveTrueOrderByName();

    Optional<Category> findByCodeAndActiveTrue(String code);
}
