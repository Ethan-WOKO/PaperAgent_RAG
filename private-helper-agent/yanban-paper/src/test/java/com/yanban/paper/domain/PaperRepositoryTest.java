package com.yanban.paper.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ContextConfiguration(classes = PaperRepositoryTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaperRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {PaperTask.class, PaperTaskRound.class})
    @EnableJpaRepositories(basePackageClasses = {PaperTaskRepository.class, PaperTaskRoundRepository.class})
    static class TestConfig {
    }

    private final PaperTaskRepository tasks;
    private final PaperTaskRoundRepository rounds;

    @Autowired
    PaperRepositoryTest(PaperTaskRepository tasks, PaperTaskRoundRepository rounds) {
        this.tasks = tasks;
        this.rounds = rounds;
    }

    @Test
    void insertPaperTaskAndRoundsThenQuery() {
        PaperTask task = tasks.save(new PaperTask(2001L, "论文任务", "paper.docx", "papers/task-1.docx", "PROCESSING", "zh", "SUMMARY", null));
        rounds.save(new PaperTaskRound(task.getId(), 1, "SUMMARY", "SUCCESS", "input", "output", "notes"));

        List<PaperTask> savedTasks = tasks.findByUserIdOrderByCreatedAtDesc(2001L);
        List<PaperTaskRound> savedRounds = rounds.findByTaskIdOrderByCreatedAtAsc(task.getId());

        assertThat(savedTasks).hasSize(1);
        assertThat(savedTasks.get(0).getTitle()).isEqualTo("论文任务");
        assertThat(savedRounds).hasSize(1);
        assertThat(savedRounds.get(0).getStage()).isEqualTo("SUMMARY");
    }
}
