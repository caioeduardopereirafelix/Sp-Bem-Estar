package com.br.spbemestar.controller;

import com.br.spbemestar.dto.NeighborhoodFeatureCollection;
import com.br.spbemestar.dto.NeighborhoodStats;
import com.br.spbemestar.service.NeighborhoodStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/neighborhoods")
@RequiredArgsConstructor
public class NeighborhoodController {

    private final NeighborhoodStatsService statsService;

    @GetMapping
    public NeighborhoodFeatureCollection map(@RequestParam(required = false) String city) {
        return statsService.map(city);
    }

    @GetMapping("/stats")
    public List<NeighborhoodStats> stats(@RequestParam(required = false) String city) {
        return statsService.ranking(city);
    }
}
