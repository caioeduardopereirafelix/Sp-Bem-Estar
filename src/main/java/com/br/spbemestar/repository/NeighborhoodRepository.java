package com.br.spbemestar.repository;

import com.br.spbemestar.model.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NeighborhoodRepository extends JpaRepository<Neighborhood, Long> {

    @Query(value = """
            SELECT * FROM neighborhood
            WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Neighborhood> findContaining(@Param("latitude") double latitude,
                                          @Param("longitude") double longitude);
}
