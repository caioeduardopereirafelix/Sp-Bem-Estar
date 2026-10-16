package com.br.spbemestar.service;

import com.br.spbemestar.dto.NeighborhoodFeatureCollection;
import com.br.spbemestar.dto.NeighborhoodStats;
import com.br.spbemestar.repository.NeighborhoodStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NeighborhoodStatsService {

    private final NeighborhoodStatsRepository stats;
    private final JsonMapper json;

    public List<NeighborhoodStats> ranking(String city) {
        return stats.findAll(city);
    }

    public NeighborhoodFeatureCollection map(String city) {
        Map<Long, String> geometries = stats.findSimplifiedGeometries(city);
        return new NeighborhoodFeatureCollection(stats.findAll(city).stream()
                .map(s -> new NeighborhoodFeatureCollection.Feature(s.id(), json.readTree(geometries.get(s.id())), s))
                .toList());
    }
}
