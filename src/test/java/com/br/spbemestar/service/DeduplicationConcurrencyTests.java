package com.br.spbemestar.service;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relatos simultâneos do mesmo problema não podem gerar chamados duplicados.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeduplicationConcurrencyTests {

    static final int PARALLEL_REPORTS = 20;
    static final double THREE_METERS = 0.000027;

    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void simultaneousReportsOfSameSpotCreateSingleIssue() throws Exception {
        double lat = -23.6000;
        double lon = -46.7000;

        List<ReportService.ReportResult> results = createInParallel(
                i -> new double[] {lat + (i % 3) * THREE_METERS / 3, lon});

        assertSingleIssue(results, lat, lon);
    }

    @Test
    void simultaneousReportsAcrossGeohashCellBorderCreateSingleIssue() throws Exception {
        double lat = -23.6100;
        // Borda entre duas células de geohash de precisão 7 (largura de 360 / 2^18 graus em longitude)
        double cellWidth = 360.0 / (1 << 18);
        double border = -180 + Math.floor((-46.7100 + 180) / cellWidth) * cellWidth;
        double west = border - THREE_METERS;
        double east = border + THREE_METERS;

        String westCell = geohash(lat, west);
        String eastCell = geohash(lat, east);
        assertThat(westCell).as("os pontos precisam cair em células diferentes").isNotEqualTo(eastCell);

        List<ReportService.ReportResult> results = createInParallel(
                i -> new double[] {lat, i % 2 == 0 ? west : east});

        assertSingleIssue(results, lat, border);
    }

    private List<ReportService.ReportResult> createInParallel(IntFunction<double[]> coordinates) throws Exception {
        byte[] photo = TestPhotos.jpeg();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReportService.ReportResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REPORTS)) {
            for (int i = 0; i < PARALLEL_REPORTS; i++) {
                double[] point = coordinates.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return reportService.create("POTHOLE", point[0], point[1], photo);
                }));
            }
            start.countDown();

            List<ReportService.ReportResult> results = new ArrayList<>();
            for (Future<ReportService.ReportResult> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private void assertSingleIssue(List<ReportService.ReportResult> results, double lat, double lon) {
        assertThat(results).extracting(ReportService.ReportResult::issueId).containsOnly(results.getFirst().issueId());
        assertThat(results).filteredOn(ReportService.ReportResult::newIssue).hasSize(1);

        Integer issuesInArea = jdbc.queryForObject("""
                SELECT count(*) FROM issue
                WHERE category_code = 'POTHOLE'
                  AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 100)
                """, Integer.class, lon, lat);
        Integer reportCount = jdbc.queryForObject("SELECT report_count FROM issue WHERE id = ?",
                Integer.class, results.getFirst().issueId());

        assertThat(issuesInArea).isEqualTo(1);
        assertThat(reportCount).isEqualTo(PARALLEL_REPORTS);
    }

    private String geohash(double lat, double lon) {
        return jdbc.queryForObject("SELECT ST_GeoHash(ST_SetSRID(ST_MakePoint(?, ?), 4326), 7)",
                String.class, lon, lat);
    }
}
