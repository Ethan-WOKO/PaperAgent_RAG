package com.yanban.core.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.core.model.ChatChunk;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatRequest;
import com.yanban.core.model.ChatResponse;
import com.yanban.core.model.ToolCall;
import com.yanban.core.model.ToolSpec;
import com.yanban.core.rag.KnowledgeContextProvider;
import com.yanban.core.rag.KnowledgeSnippet;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolRegistry;
import com.yanban.core.tool.ToolResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class HarnessEngine {

    private static final Logger log = LoggerFactory.getLogger(HarnessEngine.class);
    private static final int TOOL_PROTOCOL_PREVIEW_LIMIT = 240;
    private static final int DEFAULT_MAX_TOOL_CALLS = 6;
    private static final int DEFAULT_MAX_DUPLICATE_TOOL_CALLS = 3;
    private static final int DEFAULT_RAG_TOP_K = 5;
    private static final List<String> CURRENT_INFO_KEYWORDS = List.of(
            "最新", "当前", "现在", "最近", "今日", "今天", "实时", "新闻", "发布",
            "latest", "current", "recent", "today", "now", "news", "released", "release"
    );

    private final ChatModelProvider modelProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final KnowledgeContextProvider knowledgeContextProvider;
    private final List<ToolResultPostProcessor> toolResultPostProcessors;
    private final int ragTopK;

    public HarnessEngine(ChatModelProvider modelProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this(modelProvider, toolRegistry, objectMapper, null);
    }

    public HarnessEngine(ChatModelProvider modelProvider,
                         ToolRegistry toolRegistry,
                         ObjectMapper objectMapper,
                         KnowledgeContextProvider knowledgeContextProvider) {
        this(modelProvider, toolRegistry, objectMapper, knowledgeContextProvider, List.of());
    }

    public HarnessEngine(ChatModelProvider modelProvider,
                         ToolRegistry toolRegistry,
                         ObjectMapper objectMapper,
                         KnowledgeContextProvider knowledgeContextProvider,
                         Collection<ToolResultPostProcessor> toolResultPostProcessors) {
        this(modelProvider, toolRegistry, objectMapper, knowledgeContextProvider, toolResultPostProcessors, DEFAULT_RAG_TOP_K);
    }

    public HarnessEngine(ChatModelProvider modelProvider,
                         ToolRegistry toolRegistry,
                         ObjectMapper objectMapper,
                         KnowledgeContextProvider knowledgeContextProvider,
                         Collection<ToolResultPostProcessor> toolResultPostProcessors,
                         int ragTopK) {
        this.modelProvider = modelProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.toolResultPostProcessors = toolResultPostProcessors == null
                ? List.of()
                : List.copyOf(toolResultPostProcessors);
        this.ragTopK = Math.max(1, ragTopK);
    }

    public HarnessResult run(HarnessRequest request) {
        return run(request, null);
    }

    public HarnessResult run(HarnessRequest request, Consumer<String> tokenConsumer) {
        List<ChatMessage> messages = new ArrayList<>(request.history());
        Set<String> allowedTools = resolveAllowedTools(request);
        if (request.skillPrompt() != null && !request.skillPrompt().isBlank()) {
            messages.add(ChatMessage.system(request.skillPrompt()));
        }
        if (!request.ragDisabled() && knowledgeContextProvider != null) {
            List<KnowledgeSnippet> snippets = knowledgeContextProvider.searchContext(
                    request.userMessage(),
                    request.userId(),
                    ragTopK
            );
            if (!snippets.isEmpty()) {
                messages.add(ChatMessage.system(buildKnowledgeContext(snippets)));
            } else {
                log.info("Harness RAG returned no snippets provider={} userId={} traceId={}",
                        request.provider(),
                        request.userId(),
                        request.traceId());
            }
        }
        if (requiresCurrentWebEvidence(request.userMessage(), allowedTools)) {
            messages.add(ChatMessage.system(currentWebEvidenceInstruction()));
            messages.add(ChatMessage.system(buildCurrentWebEvidenceContext(preloadCurrentWebEvidence(request, allowedTools))));
        }
        messages.add(ChatMessage.user(request.userMessage()));

        int steps = 0;
        int toolCalls = 0;
        boolean sawSuccessfulToolResult = false;
        Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
        for (; steps < request.maxSteps(); steps++) {
            if (deadlineExceeded(request.deadlineAt())) {
                String error = "Harness deadline exceeded before model step " + (steps + 1);
                log.warn("{} traceId={}", error, request.traceId());
                if (sawSuccessfulToolResult) {
                    return synthesizeFinalAnswerAfterToolBudget(request, messages, steps, error, tokenConsumer);
                }
                return HarnessResult.failure(error, messages, steps);
            }

            ChatRequest chatRequest = new ChatRequest(
                    request.provider(),
                    request.model(),
                    List.copyOf(messages),
                    request.temperature(),
                    request.maxTokens(),
                    toolRegistry.listToolsForModel(allowedTools),
                    request.apiKey(),
                    request.apiUrl(),
                    null,
                    null,
                    request.traceId()
            );
            ChatResponse response = callModel(request, chatRequest, "agent_step", tokenConsumer);
            ChatMessage assistantMessage = response == null ? null : response.message();
            messages.add(assistantMessage);

            if (chatRequest.tools() != null && !chatRequest.tools().isEmpty()) {
                log.info("Harness step={} provider={} toolsVisible={} finishReason={} traceId={}",
                        steps + 1,
                        request.provider(),
                        extractToolNames(chatRequest.tools()),
                        response == null ? null : response.finishReason(),
                        request.traceId());
            }

            List<com.yanban.core.model.ToolCall> modelToolCalls = assistantMessage == null
                    ? null
                    : assistantMessage.toolCalls();
            if ((modelToolCalls == null || modelToolCalls.isEmpty())
                    && chatRequest.tools() != null
                    && !chatRequest.tools().isEmpty()
                    && looksLikePseudoToolCall(assistantMessage == null ? null : assistantMessage.content())) {
                log.warn("Harness suspected pseudo tool-call output provider={} step={} contentPreview={} traceId={}",
                        request.provider(),
                        steps + 1,
                        abbreviate(assistantMessage == null ? null : assistantMessage.content()),
                        request.traceId());
            }
            if (modelToolCalls == null || modelToolCalls.isEmpty()) {
                return HarnessResult.success(
                        assistantMessage == null ? null : assistantMessage.content(),
                        messages,
                        steps + 1
                );
            }

            for (com.yanban.core.model.ToolCall modelToolCall : modelToolCalls) {
                if (deadlineExceeded(request.deadlineAt())) {
                    String error = "Harness deadline exceeded before tool execution";
                    log.warn("{} traceId={} provider={}", error, request.traceId(), request.provider());
                    if (sawSuccessfulToolResult) {
                        return synthesizeFinalAnswerAfterToolBudget(request, messages, steps, error, tokenConsumer);
                    }
                    return HarnessResult.failure(error, messages, steps + 1);
                }
                if (toolCalls >= maxToolCalls(request)) {
                    String error = "Tool-call budget exceeded: maxToolCalls=" + maxToolCalls(request);
                    log.warn("{} traceId={} provider={}", error, request.traceId(), request.provider());
                    if (sawSuccessfulToolResult) {
                        return synthesizeFinalAnswerAfterToolBudget(request, messages, steps + 1, error, tokenConsumer);
                    }
                    return HarnessResult.failure(error, messages, steps + 1);
                }

                String signature = toolCallSignature(modelToolCall);
                int duplicateCount = toolCallCounts.getOrDefault(signature, 0);
                if (duplicateCount >= maxDuplicateToolCalls(request)) {
                    String error = "Duplicate tool call blocked: " + abbreviate(signature);
                    log.warn("{} traceId={} provider={}", error, request.traceId(), request.provider());
                    if (sawSuccessfulToolResult) {
                        return synthesizeFinalAnswerAfterToolBudget(request, messages, steps + 1, error, tokenConsumer);
                    }
                    return HarnessResult.failure(error, messages, steps + 1);
                }
                toolCallCounts.put(signature, duplicateCount + 1);
                toolCalls++;

                long startNanos = System.nanoTime();
                ToolResult result = executeTool(modelToolCall, request.userId(), allowedTools);
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                log.info("Harness tool step={} tool={} success={} durationMs={} traceId={}",
                        steps + 1,
                        result.toolName(),
                        result.success(),
                        durationMs,
                        request.traceId());
                ToolResult processedResult = postProcessToolResult(result, request);
                sawSuccessfulToolResult = sawSuccessfulToolResult || processedResult.success();
                messages.add(ChatMessage.tool(modelToolCall.id(), toToolMessageContent(processedResult)));
            }
        }

        String error = "Harness exceeded max_steps=" + request.maxSteps();
        log.warn("{} traceId={}", error, request.traceId());
        if (sawSuccessfulToolResult) {
            return synthesizeFinalAnswerAfterToolBudget(request, messages, steps, error, tokenConsumer);
        }
        return HarnessResult.failure(error, messages, steps);
    }

    private ChatResponse callModel(HarnessRequest request, ChatRequest chatRequest, String phase) {
        return callModel(request, chatRequest, phase, null);
    }

    private ChatResponse callModel(HarnessRequest request,
                                   ChatRequest chatRequest,
                                   String phase,
                                   Consumer<String> tokenConsumer) {
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = tokenConsumer == null
                    ? modelProvider.chat(chatRequest)
                    : streamModel(chatRequest, tokenConsumer);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            ChatResponse.Usage usage = response == null ? null : response.usage();
            log.info("Harness model phase={} provider={} model={} durationMs={} finishReason={} promptTokens={} completionTokens={} totalTokens={} traceId={}",
                    phase,
                    request.provider(),
                    request.model(),
                    durationMs,
                    response == null ? null : response.finishReason(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    usage == null ? null : usage.totalTokens(),
                    request.traceId());
            return response;
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn("Harness model failed phase={} provider={} model={} durationMs={} traceId={} error={}",
                    phase,
                    request.provider(),
                    request.model(),
                    durationMs,
                    request.traceId(),
                    blankToDefault(ex.getMessage(), ex.getClass().getSimpleName()));
            throw ex;
        }
    }

    private ChatResponse streamModel(ChatRequest chatRequest, Consumer<String> tokenConsumer) {
        StringBuilder content = new StringBuilder();
        Map<Integer, StreamingToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
        String[] finishReason = new String[1];
        modelProvider.streamChat(chatRequest)
                .doOnNext(chunk -> {
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.content() != null && !chunk.content().isEmpty()) {
                        content.append(chunk.content());
                        tokenConsumer.accept(chunk.content());
                    }
                    for (ChatChunk.ToolCallDelta delta : chunk.toolCallDeltas()) {
                        toolCallBuilders
                                .computeIfAbsent(delta.index(), StreamingToolCallBuilder::new)
                                .append(delta);
                    }
                    if (chunk.done()) {
                        finishReason[0] = chunk.finishReason();
                    }
                })
                .blockLast();

        List<ToolCall> toolCalls = toolCallBuilders.values().stream()
                .map(StreamingToolCallBuilder::build)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ChatResponse(
                new ChatMessage(
                        "assistant",
                        content.length() == 0 ? null : content.toString(),
                        toolCalls.isEmpty() ? null : toolCalls,
                        null
                ),
                finishReason[0],
                null
        );
    }

    private int maxToolCalls(HarnessRequest request) {
        return request.maxToolCalls() == null ? DEFAULT_MAX_TOOL_CALLS : Math.max(1, request.maxToolCalls());
    }

    private int maxDuplicateToolCalls(HarnessRequest request) {
        return request.maxDuplicateToolCalls() == null
                ? DEFAULT_MAX_DUPLICATE_TOOL_CALLS
                : Math.max(1, request.maxDuplicateToolCalls());
    }

    private boolean deadlineExceeded(LocalDateTime deadlineAt) {
        return deadlineAt != null && LocalDateTime.now().isAfter(deadlineAt);
    }

    private String toolCallSignature(com.yanban.core.model.ToolCall modelToolCall) {
        String name = modelToolCall.function() == null ? "<unknown>" : modelToolCall.function().name();
        String arguments = modelToolCall.function() == null ? "" : modelToolCall.function().arguments();
        return name + ":" + normalizeToolArguments(arguments);
    }

    private String normalizeToolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(arguments));
        } catch (Exception ex) {
            return arguments.trim().replaceAll("\\s+", " ");
        }
    }

    private HarnessResult synthesizeFinalAnswerAfterToolBudget(HarnessRequest request,
                                                               List<ChatMessage> messages,
                                                               int steps,
                                                               String originalError,
                                                               Consumer<String> tokenConsumer) {
        List<ChatMessage> finalMessages = new ArrayList<>(messages);
        finalMessages.add(ChatMessage.system("""
                Tool-call budget is exhausted.
                Do not call any more tools.
                Produce the best final answer for the user's current task using the tool results already present in the conversation.
                If evidence is incomplete, state the limitation clearly and provide the most useful partial answer.
                """));
        try {
            ChatRequest chatRequest = new ChatRequest(
                    request.provider(),
                    request.model(),
                    List.copyOf(finalMessages),
                    request.temperature(),
                    request.maxTokens(),
                    null,
                    request.apiKey(),
                    request.apiUrl(),
                    null,
                    null,
                    request.traceId()
            );
            ChatResponse response = callModel(request, chatRequest, "final_synthesis", tokenConsumer);
            ChatMessage assistantMessage = response == null ? null : response.message();
            if (assistantMessage != null) {
                finalMessages.add(assistantMessage);
            }
            List<com.yanban.core.model.ToolCall> toolCalls = assistantMessage == null ? null : assistantMessage.toolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                String content = assistantMessage == null ? null : assistantMessage.content();
                String safeContent = content == null || content.isBlank()
                        ? "Tool-call budget was exhausted, but the model did not produce a final answer. Please narrow the task and retry."
                        : content;
                return HarnessResult.success(safeContent, finalMessages, steps + 1);
            }
            String error = originalError + "; final no-tool synthesis still returned tool_calls";
            log.warn("{} traceId={}", error, request.traceId());
            return HarnessResult.failure(error, finalMessages, steps + 1);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            String error = originalError + "; final synthesis failed: " + message;
            log.warn("Harness final synthesis after tool budget failed provider={} traceId={}",
                    request.provider(),
                    request.traceId(),
                    ex);
            return HarnessResult.failure(error, finalMessages, steps);
        }
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

    private String abbreviate(String content, int limit) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }
        int safeLimit = Math.max(32, limit);
        return content.length() <= safeLimit
                ? content
                : content.substring(0, safeLimit) + "...";
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

    private boolean requiresCurrentWebEvidence(String userMessage, Set<String> allowedTools) {
        if (!isToolAllowed("search_web", allowedTools) || userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        return CURRENT_INFO_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean isToolAllowed(String toolName, Set<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return toolRegistry.find(toolName).isPresent();
        }
        return allowedTools.contains(toolName);
    }

    private ToolResult preloadCurrentWebEvidence(HarnessRequest request, Set<String> allowedTools) {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("query", currentWebSearchQuery(request.userMessage()));
        arguments.put("topK", 8);
        com.yanban.core.tool.ToolCall call = new com.yanban.core.tool.ToolCall(
                "preflight_search_web",
                "search_web",
                arguments
        );
        try {
            ToolExecutionContext.setCurrentUserId(request.userId());
            ToolResult result = toolRegistry.execute(call, allowedTools);
            log.info("Harness preloaded current web evidence success={} traceId={}",
                    result.success(),
                    request.traceId());
            return result;
        } catch (RuntimeException ex) {
            String message = blankToDefault(ex.getMessage(), ex.getClass().getSimpleName());
            log.warn("Harness current web evidence preload failed traceId={} error={}",
                    request.traceId(),
                    message);
            return ToolResult.failure(call.id(), call.name(), message);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    private String currentWebSearchQuery(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180);
        }
        return normalized + " latest official model release 2026";
    }

    private String currentWebEvidenceInstruction() {
        return """
                The user is asking for current, latest, recent, or time-sensitive public information.
                External web evidence is required for time-sensitive claims.
                Use search_web results and any later tool results as evidence. Prefer official vendor pages, release notes, docs, or dated announcements.
                If search results include sourceAuthority fields, prioritize official and primary_technical sources. Do not use secondary/community/media sources as the sole evidence for latest model names, release dates, pricing, or capabilities.
                If only low-authority sources are available, say confidence is limited and recommend checking official vendor pages.
                In the final answer, include source URLs or source names and retrieval limitations.
                If external search is degraded, empty, or does not support a claim, do not present that claim as the latest fact. Say the external evidence was insufficient instead of relying on model memory.
                Do not invent future model names, version numbers, dates, URLs, or vendor releases.
                """;
    }

    private String buildCurrentWebEvidenceContext(ToolResult result) {
        String evidence;
        try {
            if (result != null && result.success() && result.output() != null) {
                evidence = objectMapper.writeValueAsString(result.output());
            } else {
                evidence = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                        .put("success", false)
                        .put("error", result == null
                                ? "missing_search_result"
                                : blankToDefault(result.errorMessage(), "search_web_failed")));
            }
        } catch (Exception ex) {
            evidence = "{\"success\":false,\"error\":\"failed_to_serialize_search_evidence\"}";
        }
        return "Preloaded current web evidence from search_web. Use it as mandatory evidence for time-sensitive claims:\n"
                + abbreviate(evidence, 8000);
    }

    private String buildKnowledgeContext(List<KnowledgeSnippet> snippets) {
        StringBuilder sb = new StringBuilder("""
                Private knowledge-base snippets visible to the current user are listed below.
                Prefer this evidence when answering. Cite snippets with citationId when possible.
                If all scoreBand values are low, say retrieval confidence is limited before using general knowledge.

                """);
        for (int i = 0; i < snippets.size(); i++) {
            KnowledgeSnippet snippet = snippets.get(i);
            sb.append(i + 1)
                    .append(". citationId=")
                    .append(blankToDefault(snippet.citationId(), snippet.filename() + "#" + snippet.chunkIndex()))
                    .append(" source=")
                    .append(blankToDefault(snippet.source(), "knowledge_base"))
                    .append(" filename=")
                    .append(blankToDefault(snippet.filename(), "unknown"))
                    .append(" chunkIndex=")
                    .append(snippet.chunkIndex())
                    .append(" score=")
                    .append(snippet.score())
                    .append(" scoreBand=")
                    .append(blankToDefault(snippet.scoreBand(), "unknown"))
                    .append(" rerankScore=")
                    .append(snippet.rerankScore() == null ? "unknown" : String.format(java.util.Locale.ROOT, "%.3f", snippet.rerankScore()))
                    .append(" rerankReason=")
                    .append(blankToDefault(snippet.rerankReason(), "unknown"))
                    .append("\n")
                    .append(snippet.content())
                    .append("\n\n");
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

    private ToolResult postProcessToolResult(ToolResult result, HarnessRequest request) {
        ToolResult current = result;
        for (ToolResultPostProcessor processor : toolResultPostProcessors) {
            try {
                current = processor.process(current, request);
            } catch (Exception ex) {
                log.warn("Harness tool result post-processor failed tool={} processor={}",
                        current.toolName(),
                        processor.getClass().getSimpleName(),
                        ex);
            }
        }
        return current;
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

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class StreamingToolCallBuilder {
        private final int index;
        private String id;
        private String type;
        private String functionName;
        private final StringBuilder arguments = new StringBuilder();

        private StreamingToolCallBuilder(int index) {
            this.index = index;
        }

        private void append(ChatChunk.ToolCallDelta delta) {
            if (delta == null) {
                return;
            }
            if (StringUtils.hasText(delta.id())) {
                id = delta.id();
            }
            if (StringUtils.hasText(delta.type())) {
                type = delta.type();
            }
            if (StringUtils.hasText(delta.functionName())) {
                functionName = delta.functionName();
            }
            if (delta.argumentsDelta() != null && !delta.argumentsDelta().isEmpty()) {
                arguments.append(delta.argumentsDelta());
            }
        }

        private ToolCall build() {
            if (!StringUtils.hasText(functionName)) {
                return null;
            }
            String resolvedId = StringUtils.hasText(id) ? id : "tool_call_" + index;
            String resolvedType = StringUtils.hasText(type) ? type : "function";
            return new ToolCall(
                    resolvedId,
                    resolvedType,
                    new ToolCall.FunctionCall(functionName, arguments.toString())
            );
        }
    }
}
