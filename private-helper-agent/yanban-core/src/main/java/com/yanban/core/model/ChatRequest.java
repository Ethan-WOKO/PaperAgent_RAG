package com.yanban.core.model;

import java.util.List;

public record ChatRequest(
        String provider,
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Integer maxTokens,
        List<ToolSpec> tools,
        String apiKey
) {
    public ChatRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
    }

    public static ChatRequest simple(String model, List<ChatMessage> messages) {
        return new ChatRequest(null, model, messages, null, null, null, null);
    }
}
