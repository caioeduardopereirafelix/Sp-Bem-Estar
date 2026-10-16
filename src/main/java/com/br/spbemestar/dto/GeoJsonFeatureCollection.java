package com.br.spbemestar.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeoJsonFeatureCollection(String type, List<Feature> features) {

    public static GeoJsonFeatureCollection of(List<IssueSummary> issues) {
        return new GeoJsonFeatureCollection("FeatureCollection", issues.stream().map(Feature::of).toList());
    }

    public record Feature(String type, Long id, Point geometry, Map<String, Object> properties) {

        static Feature of(IssueSummary issue) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("category", issue.categoryCode());
            properties.put("categoryName", issue.categoryName());
            properties.put("status", issue.status());
            properties.put("reportCount", issue.reportCount());
            properties.put("priorityScore", issue.priorityScore());
            properties.put("neighborhood", issue.neighborhoodName());
            properties.put("firstReportedAt", issue.firstReportedAt());
            properties.put("lastReportedAt", issue.lastReportedAt());
            return new Feature("Feature", issue.id(), Point.of(issue.latitude(), issue.longitude()), properties);
        }
    }

    // GeoJSON usa a ordem [longitude, latitude]
    public record Point(String type, double[] coordinates) {

        static Point of(double latitude, double longitude) {
            return new Point("Point", new double[] {longitude, latitude});
        }
    }
}
