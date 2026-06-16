package com.yanban.paper.service;

import com.yanban.paper.domain.PaperTask;
import com.yanban.paper.domain.PaperTaskRepository;
import com.yanban.paper.web.PaperProcessRequest;
import com.yanban.paper.web.PaperTaskResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperTaskService {

    private final PaperTaskRepository paperTaskRepository;
    private final PaperStorageService paperStorageService;
    private final PaperOrchestrator paperOrchestrator;

    public PaperTaskService(PaperTaskRepository paperTaskRepository,
                            PaperStorageService paperStorageService,
                            PaperOrchestrator paperOrchestrator) {
        this.paperTaskRepository = paperTaskRepository;
        this.paperStorageService = paperStorageService;
        this.paperOrchestrator = paperOrchestrator;
    }

    @Transactional
    public PaperTaskResponse createTask(Long userId, PaperProcessRequest request) {
        MultipartFile file = request.file();
        validateFile(file);
        String objectKey = paperStorageService.storeOriginal(userId, file);
        String title = resolveTitle(file);
        PaperTask task = paperTaskRepository.save(new PaperTask(
                userId,
                title,
                file.getOriginalFilename(),
                objectKey,
                "PENDING",
                request.targetLanguage(),
                "UPLOAD_RECEIVED",
                null
        ));
        paperOrchestrator.startTask(task.getId());
        return PaperTaskResponse.from(task, request.scoreThreshold(), request.maxRounds(), request.innerMaxAttempts(), request.literatureCount());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 docx 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .docx 文件");
        }
    }

    @Transactional(readOnly = true)
    public PaperTaskResponse getTask(Long userId, Long taskId) {
        PaperTask task = paperTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "论文任务不存在"));
        return PaperTaskResponse.from(task, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Resource downloadResult(Long userId, Long taskId) {
        PaperTask task = paperTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "论文任务不存在"));
        if (task.getFinalObjectKey() == null || task.getFinalObjectKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "论文结果尚未生成");
        }
        return new ByteArrayResource(paperStorageService.read(task.getFinalObjectKey()));
    }

    private String resolveTitle(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return "未命名论文任务";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
