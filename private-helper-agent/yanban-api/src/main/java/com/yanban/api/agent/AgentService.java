package com.yanban.api.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.settings.SysUserSettings;
import com.yanban.api.settings.UserSettingsService;
import com.yanban.api.skills.ResolvedSkill;
import com.yanban.api.skills.SkillsService;
import com.yanban.core.agent.AgentMessage;
import com.yanban.core.agent.AgentMessageRepository;
import com.yanban.core.agent.AgentSession;
import com.yanban.core.agent.AgentSessionRepository;
import com.yanban.core.harness.HarnessEngine;
import com.yanban.core.harness.HarnessRequest;
import com.yanban.core.harness.HarnessResult;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatRequest;
import com.yanban.core.model.ChatResponse;
import com.yanban.core.model.ToolCall;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int GENERATED_TITLE_MAX_LENGTH = 40;

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final HarnessEngine harnessEngine;
    private final ObjectMapper objectMapper;
    private final UserSettingsService userSettingsService;
    private final ConversationIntentRouterService conversationIntentRouterService;
    private final SkillsService skillsService;
    private final ChatModelProvider titleModelProvider;

    public AgentService(AgentSessionRepository sessions,
                        AgentMessageRepository messages,
                        HarnessEngine harnessEngine,
                        ObjectMapper objectMapper,
                        UserSettingsService userSettingsService,
                        ConversationIntentRouterService conversationIntentRouterService,
                        SkillsService skillsService,
                        @Qualifier("chatModelProvider") ChatModelProvider titleModelProvider) {
        this.sessions = sessions;
        this.messages = messages;
        this.harnessEngine = harnessEngine;
        this.objectMapper = objectMapper;
        this.userSettingsService = userSettingsService;
        this.conversationIntentRouterService = conversationIntentRouterService;
        this.skillsService = skillsService;
        this.titleModelProvider = titleModelProvider;
    }

    @Transactional
    public AgentSessionResponse createSession(Long userId, CreateSessionRequest request) {
        SysUserSettings settings = userSettingsService.getOrCreate(userId);
        AgentSession session = new AgentSession(
                userId,
                StringUtils.hasText(request.title()) ? request.title().trim() : "新会话",
                StringUtils.hasText(request.modelProvider()) ? request.modelProvider().trim() : settings.getDefaultProvider(),
                StringUtils.hasText(request.model()) ? request.model().trim() : resolveDefaultModel(settings,
                        StringUtils.hasText(request.modelProvider()) ? request.modelProvider().trim() : settings.getDefaultProvider()),
                request.maxSteps() == null ? settings.getMaxSteps() : request.maxSteps(),
                request.ragDisabled() != null ? request.ragDisabled() : !Boolean.TRUE.equals(settings.getRagDefaultEnabled())
        );
        return AgentSessionResponse.from(sessions.saveAndFlush(session));
    }

    @Transactional(readOnly = true)
    public List<AgentSessionResponse> listSessions(Long userId) {
        return sessions.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(AgentSessionResponse::from)
                .toList();
    }

    @Transactional
    public AgentSessionResponse updateSession(Long userId, Long sessionId, UpdateSessionRequest request) {
        AgentSession session = getOwnedSession(userId, sessionId);
        if (request.title() != null) {
            String title = request.title().trim();
            if (!StringUtils.hasText(title)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标题不能为空");
            }
            session.updateTitle(title);
        }
        if (StringUtils.hasText(request.modelProvider()) || StringUtils.hasText(request.model())) {
            SysUserSettings settings = userSettingsService.getOrCreate(userId);
            String provider = normalizeProvider(
                    StringUtils.hasText(request.modelProvider()) ? request.modelProvider() : session.getModelProviderSnapshot()
            );
            String model = StringUtils.hasText(request.model())
                    ? request.model().trim()
                    : resolveDefaultModel(settings, provider);
            session.updateModel(provider, model);
        }
        if (request.maxSteps() != null) {
            session.updateMaxSteps(request.maxSteps());
        }
        if (request.ragDisabled() != null) {
            session.updateRagDisabled(request.ragDisabled());
        }
        session.touch();
        return AgentSessionResponse.from(sessions.saveAndFlush(session));
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        AgentSession session = getOwnedSession(userId, sessionId);
        messages.deleteBySessionId(session.getId());
        sessions.delete(session);
        sessions.flush();
    }

    @Transactional(readOnly = true)
    public List<AgentMessageResponse> listMessages(Long userId, Long sessionId) {
        AgentSession session = getOwnedSession(userId, sessionId);
        return messages.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(AgentMessageResponse::from)
                .toList();
    }

    @Transactional
    public SendMessageResponse sendMessage(Long userId, Long sessionId, SendMessageRequest request) {
        AgentSession session = getOwnedSession(userId, sessionId);
        List<ChatMessage> history = getHistoryMessages(session.getId());
        boolean shouldAutoGenerateTitle = shouldAutoGenerateTitle(session, history);

        ConversationIntentRouterService.IntentAction intentAction = conversationIntentRouterService.route(request.content());
        if (intentAction != null) {
            List<AgentMessage> saved = new ArrayList<>();
            saved.add(messages.save(toAgentMessage(session.getId(), userId, ChatMessage.user(request.content()))));
            saved.add(messages.save(toAgentMessage(session.getId(), userId, ChatMessage.assistant(intentAction.assistantMessage()))));
            messages.flush();
            touchAndMaybeGenerateTitle(session, userId, request.content(), shouldAutoGenerateTitle);
            return new SendMessageResponse(
                    true,
                    intentAction.assistantMessage(),
                    0,
                    null,
                    intentAction.navigationUrl(),
                    saved.stream().map(AgentMessageResponse::from).toList()
            );
        }

        boolean ragDisabled = request.ragDisabled() != null ? request.ragDisabled() : Boolean.TRUE.equals(session.getRagDisabled());
        ResolvedSkill resolvedSkill = request.skillId() == null || request.skillId().isBlank()
                ? null
                : skillsService.resolveEnabledSkill(userId, request.skillId());
        HarnessResult result = harnessEngine.run(new HarnessRequest(
                history,
                userId,
                request.content(),
                session.getModelProviderSnapshot(),
                session.getModelSnapshot(),
                null,
                null,
                session.getMaxSteps(),
                ragDisabled,
                resolveProviderApiKey(userId, session.getModelProviderSnapshot()),
                resolvedSkill == null ? null : resolvedSkill.prompt(),
                resolvedSkill == null ? null : List.copyOf(resolvedSkill.allowedTools())
        ));

        List<ChatMessage> newChatMessages = result.messages().subList(history.size(), result.messages().size());
        List<AgentMessage> saved = new ArrayList<>();
        for (ChatMessage chatMessage : newChatMessages) {
            saved.add(messages.save(toAgentMessage(session.getId(), userId, chatMessage)));
        }
        messages.flush();
        touchAndMaybeGenerateTitle(session, userId, request.content(), shouldAutoGenerateTitle);

        return new SendMessageResponse(
                result.success(),
                result.assistantContent(),
                result.steps(),
                result.errorMessage(),
                null,
                saved.stream().map(AgentMessageResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public AgentSession getOwnedSession(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistoryMessages(Long sessionId) {
        return messages.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toChatMessage)
                .toList();
    }

    @Transactional
    public AgentMessage saveMessage(Long sessionId, Long userId, ChatMessage chatMessage) {
        return messages.saveAndFlush(toAgentMessage(sessionId, userId, chatMessage));
    }

    private ChatMessage toChatMessage(AgentMessage message) {
        List<ToolCall> toolCalls = parseToolCalls(message.getToolCallsJson());
        return new ChatMessage(message.getRole(), message.getContent(), toolCalls, null);
    }

    private AgentMessage toAgentMessage(Long sessionId, Long userId, ChatMessage chatMessage) {
        return new AgentMessage(
                sessionId,
                userId,
                chatMessage.role(),
                chatMessage.content(),
                serializeToolCalls(chatMessage.toolCalls()),
                null
        );
    }

    private void touchAndMaybeGenerateTitle(AgentSession session,
                                            Long userId,
                                            String firstUserMessage,
                                            boolean shouldAutoGenerateTitle) {
        if (shouldAutoGenerateTitle) {
            String generatedTitle = generateSessionTitle(session, userId, firstUserMessage);
            if (StringUtils.hasText(generatedTitle)) {
                session.updateTitle(generatedTitle);
            }
        }
        session.touch();
        sessions.saveAndFlush(session);
    }

    private boolean shouldAutoGenerateTitle(AgentSession session, List<ChatMessage> history) {
        return history.isEmpty() && isDefaultSessionTitle(session.getTitle());
    }

    private boolean isDefaultSessionTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return true;
        }
        String normalized = title.trim();
        return "新会话".equals(normalized) || "研伴对话".equals(normalized);
    }

    private String generateSessionTitle(AgentSession session, Long userId, String firstUserMessage) {
        try {
            ChatResponse response = titleModelProvider.chat(new ChatRequest(
                    session.getModelProviderSnapshot(),
                    session.getModelSnapshot(),
                    List.of(
                            ChatMessage.system("你是一个会话标题生成器。只输出标题，不要解释，不要标点，不要引号。中文不超过16个字，英文不超过8个词。"),
                            ChatMessage.user("请根据用户第一条消息生成简洁会话标题：\n" + firstUserMessage)
                    ),
                    0.2,
                    64,
                    null,
                    resolveProviderApiKey(userId, session.getModelProviderSnapshot())
            ));
            return sanitizeGeneratedTitle(response == null || response.message() == null ? null : response.message().content(), firstUserMessage);
        } catch (Exception ex) {
            log.warn("Failed to generate title for session id={}", session.getId(), ex);
            return fallbackTitle(firstUserMessage);
        }
    }

    private String sanitizeGeneratedTitle(String generated, String fallbackSource) {
        String title = StringUtils.hasText(generated) ? generated.trim() : fallbackTitle(fallbackSource);
        int lineBreak = title.indexOf('\n');
        if (lineBreak >= 0) {
            title = title.substring(0, lineBreak).trim();
        }
        title = title.replaceAll("^[\\s\\\"'“”‘’《》]+|[\\s\\\"'“”‘’《》]+$", "")
                .replaceAll("[。！？!?，,；;：:]+$", "")
                .trim();
        if (!StringUtils.hasText(title)) {
            title = fallbackTitle(fallbackSource);
        }
        return title.length() <= GENERATED_TITLE_MAX_LENGTH
                ? title
                : title.substring(0, GENERATED_TITLE_MAX_LENGTH).trim();
    }

    private String fallbackTitle(String firstUserMessage) {
        if (!StringUtils.hasText(firstUserMessage)) {
            return "新会话";
        }
        String normalized = firstUserMessage.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 16 ? normalized : normalized.substring(0, 16).trim();
    }

    private String normalizeProvider(String provider) {
        String resolved = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : UserSettingsService.DEFAULT_PROVIDER;
        if (!UserSettingsService.DEFAULT_PROVIDER.equals(resolved) && !UserSettingsService.PROVIDER_GLM.equals(resolved)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持 deepseek 或 glm 作为模型提供方");
        }
        return resolved;
    }

    private String resolveDefaultModel(SysUserSettings settings, String provider) {
        return switch (provider == null ? "deepseek" : provider.trim().toLowerCase()) {
            case "glm" -> settings.getGlmModel();
            case "deepseek" -> settings.getDeepseekModel();
            default -> settings.getDeepseekModel();
        };
    }

    private String resolveProviderApiKey(Long userId, String provider) {
        SysUserSettings settings = userSettingsService.getOrCreate(userId);
        return switch (provider == null ? "deepseek" : provider.trim().toLowerCase()) {
            case "glm" -> userSettingsService.decryptGlmApiKey(settings);
            case "deepseek" -> userSettingsService.decryptDeepseekApiKey(settings);
            default -> null;
        };
    }

    private List<ToolCall> parseToolCalls(String toolCallsJson) {
        if (!StringUtils.hasText(toolCallsJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(toolCallsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "历史 tool_calls 解析失败", ex);
        }
    }

    private String serializeToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "tool_calls 序列化失败", ex);
        }
    }
}
