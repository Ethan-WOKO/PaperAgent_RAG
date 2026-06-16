package com.yanban.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

public class GlmModelProvider implements ChatModelProvider {

    private final GlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GlmModelProvider(GlmProperties properties) {
        this(properties, WebClient.builder(), new ObjectMapper());
    }

    public GlmModelProvider(GlmProperties properties, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "glm";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validateConfigured(request.apiKey());
        GlmChatRequest payload = toGlmRequest(request, false);
        try {
            GlmChatResponse response = webClient.post()
                    .uri(properties.getApiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey(request.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new ModelProviderException(
                                    "GLM API error: HTTP " + clientResponse.statusCode().value() + " " + body))))
                    .bodyToMono(GlmChatResponse.class)
                    .block(properties.getTimeout());
            return fromGlmResponse(response);
        } catch (WebClientResponseException ex) {
            throw new ModelProviderException("GLM API error: HTTP " + ex.getStatusCode().value() + " " + ex.getResponseBodyAsString(), ex);
        } catch (ModelProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ModelProviderException("GLM API request failed", ex);
        }
    }

    @Override
    public reactor.core.publisher.Flux<ChatChunk> streamChat(ChatRequest request) {
        validateConfigured(request.apiKey());
        GlmChatRequest payload = toGlmRequest(request, true);
        return webClient.post()
                .uri(properties.getApiUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey(request.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ModelProviderException(
                                "GLM API error: HTTP " + clientResponse.statusCode().value() + " " + body))))
                .bodyToFlux(String.class)
                .flatMapIterable(this::parseSseChunk)
                .onErrorMap(ex -> ex instanceof ModelProviderException ? ex : new ModelProviderException("GLM API stream failed", ex));
    }

    private void validateConfigured(String apiKeyOverride) {
        if (!StringUtils.hasText(properties.getApiUrl())) {
            throw new ModelProviderException("GLM apiUrl is not configured");
        }
        if (!StringUtils.hasText(resolveApiKey(apiKeyOverride))) {
            throw new ModelProviderException("GLM apiKey is not configured");
        }
    }

    private String resolveApiKey(String apiKeyOverride) {
        return StringUtils.hasText(apiKeyOverride) ? apiKeyOverride : properties.getApiKey();
    }

    private GlmChatRequest toGlmRequest(ChatRequest request, boolean stream) {
        String model = StringUtils.hasText(request.model()) ? request.model() : properties.getModel();
        Double temperature = request.temperature() != null ? request.temperature() : properties.getTemperature();
        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : properties.getMaxTokens();
        List<GlmMessage> messages = request.messages().stream()
                .map(message -> new GlmMessage(message.role(), message.content(), message.toolCalls(), message.toolCallId()))
                .toList();
        return new GlmChatRequest(model, messages, temperature, maxTokens, stream, request.tools());
    }

    private ChatResponse fromGlmResponse(GlmChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ModelProviderException("GLM API returned no choices");
        }
        GlmChoice choice = response.choices().get(0);
        GlmMessage message = choice.message();
        if (message == null) {
            throw new ModelProviderException("GLM API returned empty message");
        }
        ChatResponse.Usage usage = response.usage() == null ? null : new ChatResponse.Usage(
                intOrNull(response.usage().promptTokens()),
                intOrNull(response.usage().completionTokens()),
                intOrNull(response.usage().totalTokens())
        );
        return new ChatResponse(new ChatMessage(message.role(), message.content(), message.toolCalls(), message.toolCallId()), choice.finishReason(), usage);
    }

    private Integer intOrNull(Number number) {
        return number == null ? null : number.intValue();
    }

    private List<ChatChunk> parseSseChunk(String chunk) {
        List<ChatChunk> chunks = new ArrayList<>();
        if (!StringUtils.hasText(chunk)) {
            return chunks;
        }
        String[] lines = chunk.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            String data;
            if (line.startsWith("data:")) {
                data = line.substring("data:".length()).trim();
            } else if (line.startsWith("{") || "[DONE]".equals(line)) {
                data = line;
            } else {
                continue;
            }
            if (data.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                chunks.add(ChatChunk.done("stop"));
                continue;
            }
            chunks.addAll(parseSseData(data));
        }
        return chunks;
    }

    private List<ChatChunk> parseSseData(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choice = root.path("choices").isArray() && !root.path("choices").isEmpty() ? root.path("choices").get(0) : null;
            if (choice == null) {
                return List.of();
            }
            List<ChatChunk> chunks = new ArrayList<>();
            JsonNode delta = choice.path("delta");
            if (delta.hasNonNull("content")) {
                String content = delta.path("content").asText();
                if (!content.isEmpty()) {
                    chunks.add(ChatChunk.token(content));
                }
            }
            if (choice.hasNonNull("finish_reason")) {
                chunks.add(ChatChunk.done(choice.path("finish_reason").asText()));
            }
            return chunks;
        } catch (Exception ex) {
            throw new ModelProviderException("Failed to parse GLM SSE chunk", ex);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GlmChatRequest(String model, List<GlmMessage> messages, Double temperature,
                                  @JsonProperty("max_tokens") Integer maxTokens, Boolean stream, List<ToolSpec> tools) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GlmMessage(String role, String content,
                              @JsonProperty("tool_calls") List<ToolCall> toolCalls,
                              @JsonProperty("tool_call_id") String toolCallId) {}

    private record GlmChatResponse(List<GlmChoice> choices, GlmUsage usage) {}
    private record GlmChoice(GlmMessage message, @JsonProperty("finish_reason") String finishReason) {}
    private record GlmUsage(@JsonProperty("prompt_tokens") Number promptTokens,
                            @JsonProperty("completion_tokens") Number completionTokens,
                            @JsonProperty("total_tokens") Number totalTokens) {}
}
