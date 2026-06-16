package com.yanban.paper.service;

import java.time.Instant;

public record PaperSseEvent(
        String type,
        Long taskId,
        String message,
        String stage,
        Instant timestamp
) {
    public static PaperSseEvent of(String type, Long taskId, String message, String stage) {
        return new PaperSseEvent(type, taskId, message, stage, Instant.now());
    }
}
