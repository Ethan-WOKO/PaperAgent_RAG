package com.yanban.paper.web;

import com.yanban.paper.service.PaperEventStreamService;
import com.yanban.paper.service.PaperOrchestrator;
import com.yanban.paper.service.PaperTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class PaperController {

    private final PaperTaskService paperTaskService;
    private final PaperEventStreamService paperEventStreamService;
    private final PaperOrchestrator paperOrchestrator;

    public PaperController(PaperTaskService paperTaskService,
                           PaperEventStreamService paperEventStreamService,
                           PaperOrchestrator paperOrchestrator) {
        this.paperTaskService = paperTaskService;
        this.paperEventStreamService = paperEventStreamService;
        this.paperOrchestrator = paperOrchestrator;
    }

    @PostMapping("/api/v1/paper/process")
    @ResponseStatus(HttpStatus.CREATED)
    public PaperTaskResponse process(@AuthenticationPrincipal(expression = "id") Long userId,
                                     @Valid @ModelAttribute PaperProcessRequest request) {
        return paperTaskService.createTask(userId, request);
    }

    @GetMapping("/api/v1/paper/tasks/{taskId}")
    public PaperTaskResponse getTask(@AuthenticationPrincipal(expression = "id") Long userId,
                                     @PathVariable Long taskId) {
        return paperTaskService.getTask(userId, taskId);
    }

    @GetMapping("/api/v1/paper/events")
    public SseEmitter events(@AuthenticationPrincipal(expression = "id") Long userId,
                             @RequestParam Long taskId) {
        paperTaskService.getTask(userId, taskId);
        return paperEventStreamService.subscribe(taskId);
    }

    @PostMapping("/api/v1/paper/tasks/{taskId}/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pause(@AuthenticationPrincipal(expression = "id") Long userId,
                      @PathVariable Long taskId) {
        paperOrchestrator.pause(userId, taskId);
    }

    @PostMapping("/api/v1/paper/tasks/{taskId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resume(@AuthenticationPrincipal(expression = "id") Long userId,
                       @PathVariable Long taskId) {
        paperOrchestrator.resume(userId, taskId);
    }

    @PostMapping("/api/v1/paper/tasks/{taskId}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stop(@AuthenticationPrincipal(expression = "id") Long userId,
                     @PathVariable Long taskId) {
        paperOrchestrator.stop(userId, taskId);
    }

    @GetMapping("/api/v1/paper/tasks/{taskId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal(expression = "id") Long userId,
                                             @PathVariable Long taskId) {
        PaperTaskResponse task = paperTaskService.getTask(userId, taskId);
        Resource resource = paperTaskService.downloadResult(userId, taskId);
        String filename = (task.sourceFilename() == null || task.sourceFilename().isBlank())
                ? "paper-result.docx"
                : task.sourceFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
