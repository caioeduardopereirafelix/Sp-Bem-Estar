package com.br.spbemestar.service;

import com.br.spbemestar.config.ClassificationProperties;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.repository.ReportRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ClassificationService {

    private final ReportRepository reports;
    private final ClassificationProperties properties;
    private final MeterRegistry meters;

    /**
     * Grava o resultado do modelo no relato. Só o primeiro resultado vale: uma reentrega da mesma
     * mensagem encontra o relato já classificado e é ignorada.
     *
     * @return o status resultante, ou {@code null} se o relato já tinha sido classificado
     */
    @Transactional
    public ClassificationStatus apply(Long reportId, String predictedCategory, double confidence) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Relato", reportId));
        if (report.getClassificationStatus() != ClassificationStatus.PENDING) {
            return null;
        }
        report.applyClassification(predictedCategory,
                BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP), properties.minConfidence());
        meters.counter("spbemestar.reports.classified", "status", report.getClassificationStatus().name()).increment();
        return report.getClassificationStatus();
    }
}
