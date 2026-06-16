package com.yanban.core.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {
    List<AgentMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
