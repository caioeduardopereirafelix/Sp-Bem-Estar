package com.br.spbemestar.repository;

import com.br.spbemestar.model.IssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueStatusHistoryRepository extends JpaRepository<IssueStatusHistory, Long> {

    List<IssueStatusHistory> findByIssueIdOrderByChangedAt(Long issueId);
}
