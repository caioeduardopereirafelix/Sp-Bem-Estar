package com.br.spbemestar.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record NeighborhoodFeatureCollection(String type, List<Feature> features) {

    public NeighborhoodFeatureCollection(List<Feature> features) {
        this("FeatureCollection", features);
    }

    public record Feature(String type, Long id, JsonNode geometry, NeighborhoodStats properties) {

        public Feature(Long id, JsonNode geometry, NeighborhoodStats properties) {
            this("Feature", id, geometry, properties);
        }
    }
}
