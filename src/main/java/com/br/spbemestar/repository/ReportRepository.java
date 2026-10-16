package com.br.spbemestar.repository;

import com.br.spbemestar.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByIssueIdOrderByCreatedAt(Long issueId);
}
