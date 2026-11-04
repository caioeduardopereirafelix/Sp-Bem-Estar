package com.br.spbemestar.service;

import com.br.spbemestar.config.PrivacyProperties;
import com.br.spbemestar.dto.open311.ServiceDefinition;
import com.br.spbemestar.dto.open311.ServiceRequest;
import com.br.spbemestar.exception.Open311BadRequestException;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.CategoryRepository;
import com.br.spbemestar.repository.Open311Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class Open311Service {

    public static final int MAX_RESULTS = 1000;
    static final Duration DEFAULT_WINDOW = Duration.ofDays(90);

    private static final Set<IssueStatus> OPEN = EnumSet.of(IssueStatus.OPEN, IssueStatus.CONFIRMED,
            IssueStatus.IN_PROGRESS);
    private static final Set<IssueStatus> CLOSED = EnumSet.of(IssueStatus.RESOLVED);
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "OPEN", "Aberto", "CONFIRMED", "Confirmado", "IN_PROGRESS", "Em andamento", "RESOLVED", "Resolvido");

    private final CategoryRepository categories;
    private final Open311Repository issues;
    private final PrivacyProperties privacy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ServiceDefinition> services() {
        return categories.findByActiveTrueOrderByName().stream()
                .map(c -> new ServiceDefinition(c.getCode(), c.getName(), c.getName(), false, "realtime",
                        c.getKeywords(), c.getGroup()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> requests(String ids, String serviceCode, String startDate, String endDate,
                                         String status, String baseUrl) {
        if (ids != null && !ids.isBlank()) {
            return issues.byIds(parseIds(ids)).stream().map(row -> toRequest(row, baseUrl)).toList();
        }
        Instant end = endDate == null ? Instant.now(clock) : parseDate("end_date", endDate);
        Instant start = startDate == null ? end.minus(DEFAULT_WINDOW) : parseDate("start_date", startDate);
        if (start.isAfter(end)) {
            throw new Open311BadRequestException("start_date precisa ser anterior a end_date.");
        }
        return issues.search(blankToNull(serviceCode), parseStatus(status), start, end, MAX_RESULTS).stream()
                .map(row -> toRequest(row, baseUrl)).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> request(long id, String baseUrl) {
        List<ServiceRequest> found = issues.byIds(List.of(id)).stream().map(row -> toRequest(row, baseUrl)).toList();
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Chamado", id);
        }
        return found;
    }

    private ServiceRequest toRequest(Open311Repository.Row row, String baseUrl) {
        boolean closed = CLOSED.contains(IssueStatus.valueOf(row.status()));
        String label = STATUS_LABELS.getOrDefault(row.status(), row.status());
        String notes = row.resolutionNote() == null || row.resolutionNote().isBlank()
                ? label : label + ": " + row.resolutionNote();
        String photo = row.resolutionPhotoId() != null ? row.resolutionPhotoId() : row.reportPhotoId();
        return new ServiceRequest(
                Long.toString(row.id()),
                closed ? "closed" : "open",
                notes,
                row.categoryName(),
                row.categoryCode(),
                row.reporterCount() == 1 ? "Relatado por 1 pessoa."
                        : "Relatado por " + row.reporterCount() + " pessoas.",
                blankToNull(privacy.controller()),
                format(row.requestedAt()),
                format(row.updatedAt()),
                row.neighborhood(),
                row.latitude(),
                row.longitude(),
                photo == null ? null : baseUrl + "/api/v1/photos/" + photo);
    }

    private static Set<IssueStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        Set<IssueStatus> statuses = EnumSet.noneOf(IssueStatus.class);
        for (String value : status.split(",")) {
            switch (value.strip().toLowerCase()) {
                case "open" -> statuses.addAll(OPEN);
                case "closed" -> statuses.addAll(CLOSED);
                default -> throw new Open311BadRequestException("status aceita open, closed ou os dois.");
            }
        }
        return statuses;
    }

    private static Set<Long> parseIds(String ids) {
        Set<Long> parsed = new LinkedHashSet<>();
        try {
            Arrays.stream(ids.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(Long::valueOf)
                    .forEach(parsed::add);
        } catch (NumberFormatException e) {
            throw new Open311BadRequestException("service_request_id aceita números separados por vírgula.");
        }
        if (parsed.isEmpty() || parsed.size() > MAX_RESULTS) {
            throw new Open311BadRequestException("service_request_id aceita de 1 a " + MAX_RESULTS + " números.");
        }
        return parsed;
    }

    private static Instant parseDate(String name, String value) {
        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value.strip()).atStartOfDay(IssueExportService.CITY_ZONE).toInstant();
            } catch (DateTimeParseException again) {
                throw new Open311BadRequestException(name + " precisa estar no formato ISO 8601, por exemplo "
                        + "2026-11-01T00:00:00-03:00.");
            }
        }
    }

    private static String format(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).atZone(IssueExportService.CITY_ZONE)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
