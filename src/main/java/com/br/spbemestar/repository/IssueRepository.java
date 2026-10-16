package com.br.spbemestar.repository;

import com.br.spbemestar.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query(value = """
            SELECT i.* FROM issue i
            WHERE i.category_code = :category
              AND i.status <> 'RESOLVED'
              AND i.last_reported_at > :since
              AND ST_DWithin(i.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius)
            ORDER BY i.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearestOpen(@Param("category") String category,
                                    @Param("latitude") double latitude,
                                    @Param("longitude") double longitude,
                                    @Param("radius") double radiusMeters,
                                    @Param("since") Instant since);
}
