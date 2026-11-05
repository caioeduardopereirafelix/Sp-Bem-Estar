package com.br.spbemestar.service;

import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.IssueQueryRepository;
import com.br.spbemestar.util.PhotoUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueExportService {

    public static final ZoneId CITY_ZONE = ZoneId.of("America/Sao_Paulo");

    static final List<String> HEADER = List.of("Chamado", "Tipo", "Situação", "Distrito", "Latitude", "Longitude",
            "Relatos", "Pessoas", "Prioridade", "Primeiro relato", "Último relato", "Resolvido em",
            "Nota da resolução", "Foto da resolução", "Link");

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "OPEN", "Aberto", "CONFIRMED", "Confirmado", "IN_PROGRESS", "Em andamento", "RESOLVED", "Resolvido");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final IssueQueryRepository issueQueries;

    @Transactional(readOnly = true)
    public int writeCsv(Writer out, IssueStatus status, String category, LocalDate from, LocalDate to,
                        String baseUrl, Long operatorId) {
        Instant start = from == null ? null : from.atStartOfDay(CITY_ZONE).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(CITY_ZONE).toInstant();
        DecimalFormat coordinate = decimal("0.000000");
        DecimalFormat score = decimal("0.00");
        int[] count = {0};
        try {
            out.write('﻿');
            writeLine(out, HEADER);
            issueQueries.export(status, category, start, end, rs -> {
                writeLine(out, row(rs, baseUrl, coordinate, score));
                count[0]++;
            });
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Planilha de chamados exportada pelo operador {}: {} chamado(s)", operatorId, count[0]);
        return count[0];
    }

    private static List<String> row(ResultSet rs, String baseUrl, DecimalFormat coordinate, DecimalFormat score)
            throws SQLException {
        long id = rs.getLong("id");
        String photoId = rs.getString("resolution_photo_id");
        BigDecimal priority = rs.getBigDecimal("priority_score");
        return List.of(
                Long.toString(id),
                text(rs.getString("category_name")),
                STATUS_LABELS.getOrDefault(rs.getString("status"), rs.getString("status")),
                text(rs.getString("neighborhood_name")),
                coordinate.format(rs.getDouble("latitude")),
                coordinate.format(rs.getDouble("longitude")),
                Integer.toString(rs.getInt("report_count")),
                Integer.toString(rs.getInt("reporter_count")),
                priority == null ? "" : score.format(priority),
                dateTime(rs, "first_reported_at"),
                dateTime(rs, "last_reported_at"),
                dateTime(rs, "resolved_at"),
                text(rs.getString("resolution_note")),
                photoId == null ? "" : PhotoUrls.absolute(baseUrl, photoId),
                baseUrl + "/?issue=" + id);
    }

    private static String dateTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? "" : value.atZoneSameInstant(CITY_ZONE).format(DATE_TIME);
    }

    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static void writeLine(Writer out, List<String> cells) {
        try {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    out.write(';');
                }
                out.write(quote(cells.get(i)));
            }
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String quote(String cell) {
        if (cell.indexOf(';') < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0 && cell.indexOf('\r') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    private static DecimalFormat decimal(String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
    }
}
