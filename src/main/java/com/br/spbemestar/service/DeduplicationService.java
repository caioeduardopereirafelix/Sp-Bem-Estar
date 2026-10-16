package com.br.spbemestar.service;

import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.NeighborhoodRepository;
import com.br.spbemestar.util.GeoPoints;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private static final int LOCK_NAMESPACE = 1;
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320;

    private final IssueRepository issues;
    private final NeighborhoodRepository neighborhoods;
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public Result attachOrOpen(Category category, double latitude, double longitude, Instant now) {
        lockArea(category, latitude, longitude);

        Instant since = now.minus(Duration.ofDays(category.getDedupWindowDays()));
        return issues.findNearestOpen(
                        category.getCode(), latitude, longitude, category.getDedupRadiusMeters(), since)
                .map(existing -> {
                    existing.registerReport(now);
                    return new Result(existing, false);
                })
                .orElseGet(() -> {
                    var neighborhood = neighborhoods.findContaining(latitude, longitude).orElse(null);
                    Issue created = Issue.open(category, GeoPoints.of(latitude, longitude), neighborhood, now);
                    return new Result(issues.save(created), true);
                });
    }

    private void lockArea(Category category, double latitude, double longitude) {
        double radius = category.getDedupRadiusMeters();
        double dLat = radius / METERS_PER_DEGREE_LATITUDE;
        double dLon = radius / (METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude)));
        int precision = radius <= 60 ? 7 : 6;

        @SuppressWarnings("unchecked")
        List<Integer> keys = em.createNativeQuery("""
                SELECT DISTINCT hashtext(:category || ':' || ST_GeoHash(ST_SetSRID(ST_MakePoint(c.lon, c.lat), 4326), :precision)) AS k
                FROM (VALUES (:west, :south), (:west, :north), (:east, :south), (:east, :north)) AS c(lon, lat)
                ORDER BY k
                """)
                .setParameter("category", category.getCode())
                .setParameter("precision", precision)
                .setParameter("west", longitude - dLon)
                .setParameter("east", longitude + dLon)
                .setParameter("south", latitude - dLat)
                .setParameter("north", latitude + dLat)
                .getResultList();

        for (int key : keys) {
            em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:namespace, :key)")
                    .setParameter("namespace", LOCK_NAMESPACE)
                    .setParameter("key", key)
                    .getSingleResult();
        }
    }

    public record Result(Issue issue, boolean created) {
    }
}
