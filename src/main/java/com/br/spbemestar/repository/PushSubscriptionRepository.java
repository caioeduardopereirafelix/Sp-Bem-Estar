package com.br.spbemestar.repository;

import com.br.spbemestar.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByReporterHashOrderByUpdatedAtDesc(String reporterHash);

    List<PushSubscription> findByReporterHashIn(Collection<String> reporterHashes);

    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.reporterHash = :reporterHash")
    int deleteByEndpointAndReporterHash(@Param("endpoint") String endpoint, @Param("reporterHash") String reporterHash);
}
