package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "category")
public class Category {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "dedup_radius_m", nullable = false)
    private int dedupRadiusMeters;

    @Column(name = "dedup_window_days", nullable = false)
    private int dedupWindowDays;

    @Column(name = "severity_weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal severityWeight;

    @Column(nullable = false)
    private boolean active;
}
