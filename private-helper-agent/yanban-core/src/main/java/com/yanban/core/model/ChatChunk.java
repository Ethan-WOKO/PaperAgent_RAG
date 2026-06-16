package com.yanban.core.model;

public record ChatChunk(
        String content,
        boolean done,
        String finishReason
) {
    public static ChatChunk token(String content) {
        return new ChatChunk(content, false, null);
    }

    public static ChatChunk done(String finishReason) {
        return new ChatChunk(null, true, finishReason);
    }
}
