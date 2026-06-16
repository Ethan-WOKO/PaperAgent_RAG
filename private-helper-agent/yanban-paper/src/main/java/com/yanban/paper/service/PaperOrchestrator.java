package com.yanban.paper.service;

import com.yanban.paper.domain.PaperTask;
import com.yanban.paper.domain.PaperTaskRepository;
import com.yanban.paper.domain.PaperTaskRound;
import com.yanban.paper.domain.PaperTaskRoundRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperOrchestrator {

    private final PaperTaskRepository tasks;
    private final PaperTaskRoundRepository rounds;
    private final PaperEventStreamService eventStreamService;
    private final PaperStorageService paperStorageService;
    private final Executor paperTaskExecutor;
    private final Map<Long, ControlState> controlStates = new ConcurrentHashMap<>();

    public PaperOrchestrator(PaperTaskRepository tasks,
                             PaperTaskRoundRepository rounds,
                             PaperEventStreamService eventStreamService,
                             PaperStorageService paperStorageService,
                             @Qualifier("paperTaskExecutor") Executor paperTaskExecutor) {
        this.tasks = tasks;
        this.rounds = rounds;
        this.eventStreamService = eventStreamService;
        this.paperStorageService = paperStorageService;
        this.paperTaskExecutor = paperTaskExecutor;
    }

    public void startTask(Long taskId) {
        controlStates.putIfAbsent(taskId, new ControlState());
        paperTaskExecutor.execute(() -> runTask(taskId));
    }

    @Transactional
    public void pause(Long userId, Long taskId) {
        PaperTask task = getOwnedTask(userId, taskId);
        controlStates.computeIfAbsent(taskId, key -> new ControlState()).paused = true;
        task.setStatus("PAUSED");
        task.setCurrentStage(task.getCurrentStage() == null ? "PAUSED" : task.getCurrentStage());
        eventStreamService.publish(PaperSseEvent.of("paused", taskId, "任务已暂停", task.getCurrentStage()));
    }

    @Transactional
    public void resume(Long userId, Long taskId) {
        PaperTask task = getOwnedTask(userId, taskId);
        ControlState state = controlStates.computeIfAbsent(taskId, key -> new ControlState());
        state.paused = false;
        task.setStatus("RUNNING");
        eventStreamService.publish(PaperSseEvent.of("log", taskId, "任务继续执行", task.getCurrentStage()));
    }

    @Transactional
    public void stop(Long userId, Long taskId) {
        PaperTask task = getOwnedTask(userId, taskId);
        controlStates.computeIfAbsent(taskId, key -> new ControlState()).stopped = true;
        task.setStatus("STOPPED");
        task.setCurrentStage("STOPPED");
        eventStreamService.publish(PaperSseEvent.of("error", taskId, "任务已停止", "STOPPED"));
    }

    private void runTask(Long taskId) {
        try {
            PaperTask task = tasks.findById(taskId).orElseThrow();
            transition(taskId, "RUNNING", "SUMMARY", null);
            publish("log", taskId, "开始生成摘要", "SUMMARY");
            checkpoint(taskId);
            persistRound(taskId, 1, "SUMMARY", "RUNNING", "original-docx", "summary draft", "summary stage");
            publish("summary_ready", taskId, "摘要已生成", "SUMMARY");

            transition(taskId, "RUNNING", "SECTIONS", null);
            publish("sections", taskId, "开始章节处理", "SECTIONS");
            publish("outer_round", taskId, "开始外层第 1 轮", "SECTIONS");
            publish("section_loop_start", taskId, "开始处理章节 Introduction", "SECTIONS");
            checkpoint(taskId);
            publish("section_attempt", taskId, "章节尝试 1/1", "SECTIONS");
            persistRound(taskId, 2, "SECTION_INTRO", "RUNNING", "intro input", "intro polished", "Introduction");
            publish("section_polished", taskId, "章节润色完成", "SECTIONS");
            publish("section_review_done", taskId, "章节审查完成", "SECTIONS");

            transition(taskId, "RUNNING", "PAPER_REVIEW", null);
            publish("paper_review_done", taskId, "跨章审查完成", "PAPER_REVIEW");
            persistRound(taskId, 3, "PAPER_REVIEW", "RUNNING", "paper review input", "paper review output", "cross-section review");
            checkpoint(taskId);

            transition(taskId, "RUNNING", "ABSTRACT", null);
            publish("review", taskId, "开始生成摘要润色结果", "ABSTRACT");
            persistRound(taskId, 4, "ABSTRACT", "RUNNING", "abstract input", "abstract output", "abstract stage");
            checkpoint(taskId);

            transition(taskId, "RUNNING", "REFERENCES", null);
            publish("references_ready", taskId, "已生成文献推荐占位结果", "REFERENCES");
            persistRound(taskId, 5, "REFERENCES", "RUNNING", "reference input", "reference output", "openalex placeholder");
            checkpoint(taskId);

            String finalObjectKey = paperStorageService.storeFinal(task.getUserId(), task.getSourceFilename(), paperStorageService.read(task.getObjectKey()));
            transitionComplete(taskId, finalObjectKey);
            publish("complete", taskId, "论文任务已完成（当前为最小骨架流程）", "COMPLETE");
        } catch (TaskStoppedException ex) {
            transition(taskId, "STOPPED", "STOPPED", ex.getMessage());
            publish("error", taskId, ex.getMessage(), "STOPPED");
        } catch (Exception ex) {
            transition(taskId, "FAILED", "FAILED", ex.getMessage());
            publish("error", taskId, "论文任务失败: " + ex.getMessage(), "FAILED");
        }
    }

    private void checkpoint(Long taskId) {
        ControlState state = controlStates.computeIfAbsent(taskId, key -> new ControlState());
        if (state.stopped) {
            throw new TaskStoppedException("任务已被停止");
        }
        while (state.paused) {
            sleep(200);
            if (state.stopped) {
                throw new TaskStoppedException("任务已被停止");
            }
        }
        sleep(200);
    }

    @Transactional
    protected void transition(Long taskId, String status, String stage, String errorMessage) {
        PaperTask task = tasks.findById(taskId).orElseThrow();
        task.setStatus(status);
        task.setCurrentStage(stage);
        task.setErrorMessage(errorMessage);
        tasks.save(task);
    }

    @Transactional
    protected void persistRound(Long taskId, int roundNumber, String stage, String status,
                                String inputText, String outputText, String notes) {
        rounds.save(new PaperTaskRound(taskId, roundNumber, stage, status, inputText, outputText, notes));
    }

    @Transactional
    protected void transitionComplete(Long taskId, String finalObjectKey) {
        PaperTask task = tasks.findById(taskId).orElseThrow();
        task.setFinalObjectKey(finalObjectKey);
        task.setStatus("COMPLETED");
        task.setCurrentStage("COMPLETE");
        task.setErrorMessage(null);
        tasks.save(task);
    }

    private void publish(String type, Long taskId, String message, String stage) {
        eventStreamService.publish(PaperSseEvent.of(type, taskId, message, stage));
    }

    private PaperTask getOwnedTask(Long userId, Long taskId) {
        return tasks.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "论文任务不存在"));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("任务线程被中断", ex);
        }
    }

    private static final class ControlState {
        private volatile boolean paused;
        private volatile boolean stopped;
    }

    private static final class TaskStoppedException extends RuntimeException {
        private TaskStoppedException(String message) {
            super(message);
        }
    }
}
