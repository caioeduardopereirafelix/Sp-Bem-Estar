package com.br.spbemestar.repository;

import com.br.spbemestar.model.IssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IssueStatusHistoryRepository extends JpaRepository<IssueStatusHistory, Long> {

    List<IssueStatusHistory> findByIssueIdOrderByChangedAt(Long issueId);

    List<IssueStatusHistory> findByIssueIdInOrderByChangedAt(Collection<Long> issueIds);
}
