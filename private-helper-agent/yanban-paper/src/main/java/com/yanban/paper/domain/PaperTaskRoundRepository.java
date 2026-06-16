package com.yanban.paper.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperTaskRoundRepository extends JpaRepository<PaperTaskRound, Long> {
    List<PaperTaskRound> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
