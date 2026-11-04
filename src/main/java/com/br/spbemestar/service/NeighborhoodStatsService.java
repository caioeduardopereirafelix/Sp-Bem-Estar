package com.br.spbemestar.service;

import com.br.spbemestar.dto.NeighborhoodFeatureCollection;
import com.br.spbemestar.dto.NeighborhoodStats;
import com.br.spbemestar.repository.NeighborhoodStatsRepository;
import com.br.spbemestar.service.cache.JsonCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NeighborhoodStatsService {

    static final String GEOMETRY_CACHE = "district-geometries";
    static final Duration GEOMETRY_TTL = Duration.ofDays(1);

    private final NeighborhoodStatsRepository stats;
    private final JsonMapper json;
    private final JsonCache cache;

    public List<NeighborhoodStats> ranking(String city) {
        return stats.findAll(city);
    }

    public NeighborhoodFeatureCollection map(String city) {
        Map<Long, String> geometries = cache.get(GEOMETRY_CACHE, city == null ? "todas" : city, GEOMETRY_TTL,
                json.getTypeFactory().constructMapType(HashMap.class, Long.class, String.class),
                () -> stats.findSimplifiedGeometries(city));
        return new NeighborhoodFeatureCollection(stats.findAll(city).stream()
                .map(s -> new NeighborhoodFeatureCollection.Feature(s.id(), json.readTree(geometries.get(s.id())), s))
                .toList());
    }
}
