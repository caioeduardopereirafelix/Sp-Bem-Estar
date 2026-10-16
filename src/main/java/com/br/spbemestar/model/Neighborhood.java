package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiPolygon;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "neighborhood")
public class Neighborhood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private Integer population;

    @Column(nullable = false, columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon geom;

    public Neighborhood(String name, String city, Integer population, MultiPolygon geom) {
        this.name = name;
        this.city = city;
        this.population = population;
        this.geom = geom;
    }
}
