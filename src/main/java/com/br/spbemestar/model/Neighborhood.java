package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.MultiPolygon;

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

    // geometry (não geography): usado em ST_Contains, mais rápido no plano
    @Column(nullable = false, columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon geom;

    protected Neighborhood() {
    }

    public Neighborhood(String name, String city, Integer population, MultiPolygon geom) {
        this.name = name;
        this.city = city;
        this.population = population;
        this.geom = geom;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public Integer getPopulation() {
        return population;
    }

    public MultiPolygon getGeom() {
        return geom;
    }
}
