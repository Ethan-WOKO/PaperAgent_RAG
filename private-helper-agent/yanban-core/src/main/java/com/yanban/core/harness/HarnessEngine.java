package com.yanban.core.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatRequest;
import com.yanban.core.model.ChatResponse;
import com.yanban.core.model.ToolSpec;
import com.yanban.core.rag.KnowledgeContextProvider;
import com.yanban.core.rag.KnowledgeSnippet;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolRegistry;
import com.yanban.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarnessEngine {

    private static final Logger log = LoggerFactory.getLogger(HarnessEngine.class);
    private static final int TOOL_PROTOCOL_PREVIEW_LIMIT = 240;

    private final ChatModelProvider modelProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final KnowledgeContextProvider knowledgeContextProvider;

    public HarnessEngine(ChatModelProvider modelProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this(modelProvider, toolRegistry, objectMapper, null);
    }

    public HarnessEngine(ChatModelProvider modelProvider,
                         ToolRegistry toolRegistry,
                         ObjectMapper objectMapper,
                         KnowledgeContextProvider knowledgeContextProvider) {
        this.modelProvider = modelProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.knowledgeContextProvider = knowledgeContextProvider;
    }

    public HarnessResult run(HarnessRequest request) {
        List<ChatMessage> messages = new ArrayList<>(request.history());
        if (request.skillPrompt() != null && !request.skillPrompt().isBlank()) {
            messages.add(ChatMessage.system(request.skillPrompt()));
        }
        if (!request.ragDisabled() && knowledgeContextProvider != null) {
            List<KnowledgeSnippet> snippets = knowledgeContextProvider.searchContext(request.userMessage(), request.userId(), 3);
            if (!snippets.isEmpty()) {
                messages.add(ChatMessage.system(buildKnowledgeContext(snippets)));
            }
        }
        messages.add(ChatMessage.user(request.userMessage()));

        Set<String> allowedTools = resolveAllowedTools(request);
        int steps = 0;
        for (; steps < request.maxSteps(); steps++) {
            ChatRequest chatRequest = new ChatRequest(
                    request.provider(),
                    request.model(),
                    List.copyOf(messages),
                    request.temperature(),
                    request.maxTokens(),
                    toolRegistry.listToolsForModel(allowedTools),
                    request.apiKey()
            );
            ChatResponse response = modelProvider.chat(chatRequest);
            ChatMessage assistantMessage = response.message();
            messages.add(assistantMessage);

            if (chatRequest.tools() != null && !chatRequest.tools().isEmpty()) {
                log.info("Harness step={} provider={} toolsVisible={} finishReason={}",
                        steps + 1,
                        request.provider(),
                        extractToolNames(chatRequest.tools()),
                        response.finishReason());
            }

            List<com.yanban.core.model.ToolCall> modelToolCalls = assistantMessage == null ? null : assistantMessage.toolCalls();
            if ((modelToolCalls == null || modelToolCalls.isEmpty())
                    && chatRequest.tools() != null
                    && !chatRequest.tools().isEmpty()
                    && looksLikePseudoToolCall(assistantMessage == null ? null : assistantMessage.content())) {
                log.warn("Harness suspected pseudo tool-call output provider={} step={} contentPreview={}",
                        request.provider(),
                        steps + 1,
                        abbreviate(assistantMessage == null ? null : assistantMessage.content()));
            }
            if (modelToolCalls == null || modelToolCalls.isEmpty()) {
                return HarnessResult.success(assistantMessage == null ? null : assistantMessage.content(), messages, steps + 1);
            }

            for (com.yanban.core.model.ToolCall modelToolCall : modelToolCalls) {
                long startNanos = System.nanoTime();
                ToolResult result = executeTool(modelToolCall, request.userId(), allowedTools);
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                log.info("Harness tool step={} tool={} success={} durationMs={}",
                        steps + 1, result.toolName(), result.success(), durationMs);
                messages.add(ChatMessage.tool(modelToolCall.id(), toToolMessageContent(result)));
            }
        }

        String error = "Harness exceeded max_steps=" + request.maxSteps();
        log.warn(error);
        return HarnessResult.failure(error, messages, steps);
    }

    private List<String> extractToolNames(List<ToolSpec> tools) {
        return tools.stream()
                .map(tool -> tool.function() == null ? "<unknown>" : tool.function().name())
                .toList();
    }

    private boolean looksLikePseudoToolCall(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains("<tool_call>")
                || normalized.contains("</tool_call>")
                || normalized.contains("<path>")
                || normalized.contains("<arg_key>")
                || normalized.contains("mcp_fs__")
                || normalized.contains("filesystem");
    }

    private String abbreviate(String content) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }
        return content.length() <= TOOL_PROTOCOL_PREVIEW_LIMIT
                ? content
                : content.substring(0, TOOL_PROTOCOL_PREVIEW_LIMIT) + "...";
    }

    private Set<String> resolveAllowedTools(HarnessRequest request) {
        Set<String> allowed = request.allowedToolNames() == null
                ? new LinkedHashSet<>(toolRegistry.listToolNames())
                : new LinkedHashSet<>(request.allowedToolNames());
        if (request.ragDisabled()) {
            allowed.remove("search_knowledge");
        }
        return allowed;
    }

    private String buildKnowledgeContext(List<KnowledgeSnippet> snippets) {
        StringBuilder sb = new StringBuilder("以下是当前用户可见的知识库检索结果，请优先参考这些内容回答：\n");
        for (int i = 0; i < snippets.size(); i++) {
            KnowledgeSnippet snippet = snippets.get(i);
            sb.append(i + 1)
                    .append(". [")
                    .append(snippet.filename())
                    .append("#")
                    .append(snippet.chunkIndex())
                    .append("] ")
                    .append(snippet.content())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private ToolResult executeTool(com.yanban.core.model.ToolCall modelToolCall, Long userId, Set<String> allowedTools) {
        JsonNode arguments = parseArguments(modelToolCall);
        com.yanban.core.tool.ToolCall toolCall = new com.yanban.core.tool.ToolCall(
                modelToolCall.id(),
                modelToolCall.function().name(),
                arguments
        );
        try {
            ToolExecutionContext.setCurrentUserId(userId);
            return toolRegistry.execute(toolCall, allowedTools);
        } catch (RuntimeException ex) {
            return ToolResult.failure(toolCall.id(), toolCall.name(), ex.getMessage());
        } finally {
            ToolExecutionContext.clear();
        }
    }

    private JsonNode parseArguments(com.yanban.core.model.ToolCall modelToolCall) {
        String rawArguments = modelToolCall.function() == null ? null : modelToolCall.function().arguments();
        if (rawArguments == null || rawArguments.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(rawArguments);
        } catch (Exception ex) {
            throw new HarnessException("Failed to parse tool arguments for " + modelToolCall.function().name(), ex);
        }
    }

    private String toToolMessageContent(ToolResult result) {
        try {
            if (result.success()) {
                return objectMapper.writeValueAsString(result.output());
            }
            return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("success", false)
                    .put("error", result.errorMessage()));
        } catch (Exception ex) {
            throw new HarnessException("Failed to serialize tool result", ex);
        }
    }
}
