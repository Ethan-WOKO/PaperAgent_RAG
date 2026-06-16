package com.yanban.core.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.model.ChatChunk;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatRequest;
import com.yanban.core.model.ChatResponse;
import com.yanban.core.model.ToolCall;
import com.yanban.core.rag.KnowledgeContextProvider;
import com.yanban.core.rag.KnowledgeSnippet;
import com.yanban.core.tool.EchoToolExecutor;
import com.yanban.core.tool.ToolRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class HarnessEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runsToolCallThenFinalAssistantMessage() {
        MockProvider provider = new MockProvider(List.of(
                new ChatResponse(
                        new ChatMessage("assistant", null, List.of(new ToolCall(
                                "call-1",
                                "function",
                                new ToolCall.FunctionCall("echo", "{\"message\":\"hello\"}")
                        )), null),
                        "tool_calls",
                        null
                ),
                new ChatResponse(ChatMessage.assistant("工具返回了 hello"), "stop", null)
        ));
        ToolRegistry registry = new ToolRegistry().register(new EchoToolExecutor(objectMapper));
        HarnessEngine engine = new HarnessEngine(provider, registry, objectMapper);

        HarnessResult result = engine.run(new HarnessRequest(
                List.of(ChatMessage.system("你是研伴 Agent。")),
                1001L,
                "请调用 echo",
                "mock",
                "mock-model",
                null,
                null,
                20,
                true,
                null,
                null,
                null
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.assistantContent()).isEqualTo("工具返回了 hello");
        assertThat(result.steps()).isEqualTo(2);
        assertThat(provider.requests).hasSize(2);
        assertThat(provider.requests.get(0).tools()).hasSize(1);
        assertThat(result.messages()).extracting(ChatMessage::role)
                .containsExactly("system", "user", "assistant", "tool", "assistant");
        ChatMessage toolMessage = result.messages().get(3);
        assertThat(toolMessage.toolCallId()).isEqualTo("call-1");
        assertThat(toolMessage.content()).contains("hello");
    }

    @Test
    void stopsWhenModelReturnsPlainTextImmediately() {
        MockProvider provider = new MockProvider(List.of(
                new ChatResponse(ChatMessage.assistant("直接回答"), "stop", null)
        ));
        HarnessEngine engine = new HarnessEngine(provider, new ToolRegistry().register(new EchoToolExecutor(objectMapper)), objectMapper);

        HarnessResult result = engine.run(new HarnessRequest(List.of(), 1001L, "你好", "mock", "mock-model", 0.1, 128, 20, true, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.assistantContent()).isEqualTo("直接回答");
        assertThat(result.steps()).isEqualTo(1);
        assertThat(result.messages()).extracting(ChatMessage::role).containsExactly("user", "assistant");
    }

    @Test
    void usesKnowledgeContextWhenRagEnabled() {
        MockProvider provider = new MockProvider(List.of(
                new ChatResponse(ChatMessage.assistant("参考知识库回答"), "stop", null)
        ));
        FakeKnowledgeContextProvider knowledge = new FakeKnowledgeContextProvider(List.of(
                new KnowledgeSnippet(1L, "paper.md", 0, "alpha knowledge", 1.0)
        ));
        HarnessEngine engine = new HarnessEngine(provider, new ToolRegistry().register(new EchoToolExecutor(objectMapper)), objectMapper, knowledge);

        HarnessResult result = engine.run(new HarnessRequest(List.of(), 1001L, "alpha", "mock", "mock-model", null, null, 20, false, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(knowledge.called).isTrue();
        assertThat(provider.requests.get(0).messages()).extracting(ChatMessage::role)
                .containsExactly("system", "user");
        assertThat(provider.requests.get(0).messages().get(0).content()).contains("alpha knowledge");
    }

    @Test
    void skipsKnowledgeContextWhenRagDisabled() {
        MockProvider provider = new MockProvider(List.of(
                new ChatResponse(ChatMessage.assistant("不查知识库"), "stop", null)
        ));
        FakeKnowledgeContextProvider knowledge = new FakeKnowledgeContextProvider(List.of(
                new KnowledgeSnippet(1L, "paper.md", 0, "alpha knowledge", 1.0)
        ));
        HarnessEngine engine = new HarnessEngine(provider, new ToolRegistry().register(new EchoToolExecutor(objectMapper)), objectMapper, knowledge);

        HarnessResult result = engine.run(new HarnessRequest(List.of(), 1001L, "alpha", "mock", "mock-model", null, null, 20, true, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(knowledge.called).isFalse();
        assertThat(provider.requests.get(0).messages()).extracting(ChatMessage::role)
                .containsExactly("user");
    }

    @Test
    void maxStepsTerminatesContinuousToolCalls() {
        ChatResponse toolCallResponse = new ChatResponse(
                new ChatMessage("assistant", null, List.of(new ToolCall(
                        "call-loop",
                        "function",
                        new ToolCall.FunctionCall("echo", "{\"message\":\"loop\"}")
                )), null),
                "tool_calls",
                null
        );
        MockProvider provider = new MockProvider(List.of(toolCallResponse, toolCallResponse, toolCallResponse));
        HarnessEngine engine = new HarnessEngine(provider, new ToolRegistry().register(new EchoToolExecutor(objectMapper)), objectMapper);

        HarnessResult result = engine.run(new HarnessRequest(List.of(), 1001L, "循环调用", "mock", "mock-model", null, null, 2, true, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("max_steps=2");
        assertThat(result.steps()).isEqualTo(2);
        assertThat(provider.requests).hasSize(2);
        assertThat(result.messages()).extracting(ChatMessage::role)
                .containsExactly("user", "assistant", "tool", "assistant", "tool");
    }

    private static class FakeKnowledgeContextProvider implements KnowledgeContextProvider {
        private final List<KnowledgeSnippet> snippets;
        private boolean called;

        private FakeKnowledgeContextProvider(List<KnowledgeSnippet> snippets) {
            this.snippets = snippets;
        }

        @Override
        public List<KnowledgeSnippet> searchContext(String query, Long userId, int topK) {
            called = true;
            return snippets;
        }
    }

    private static class MockProvider implements ChatModelProvider {
        private final Queue<ChatResponse> responses;
        private final List<ChatRequest> requests = new ArrayList<>();

        private MockProvider(List<ChatResponse> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String providerName() {
            return "mock";
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return responses.remove();
        }

        @Override
        public Flux<ChatChunk> streamChat(ChatRequest request) {
            return Flux.empty();
        }
    }
}
