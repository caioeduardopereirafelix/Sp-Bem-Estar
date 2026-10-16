package com.br.spbemestar.service;

import com.br.spbemestar.dto.NeighborhoodStats;
import com.br.spbemestar.repository.NeighborhoodStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NeighborhoodStatsService {

    private final NeighborhoodStatsRepository stats;

    public List<NeighborhoodStats> ranking(String city) {
        return stats.findAll(city);
    }
}
