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
import com.yanban.core.model.ToolCall;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentService {

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final HarnessEngine harnessEngine;
    private final ObjectMapper objectMapper;
    private final UserSettingsService userSettingsService;
    private final PaperRevisionIntentService paperRevisionIntentService;
    private final SkillsService skillsService;

    public AgentService(AgentSessionRepository sessions,
                        AgentMessageRepository messages,
                        HarnessEngine harnessEngine,
                        ObjectMapper objectMapper,
                        UserSettingsService userSettingsService,
                        PaperRevisionIntentService paperRevisionIntentService,
                        SkillsService skillsService) {
        this.sessions = sessions;
        this.messages = messages;
        this.harnessEngine = harnessEngine;
        this.objectMapper = objectMapper;
        this.userSettingsService = userSettingsService;
        this.paperRevisionIntentService = paperRevisionIntentService;
        this.skillsService = skillsService;
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
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AgentSessionResponse::from)
                .toList();
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

        PaperRevisionIntentService.PaperRevisionSuggestion suggestion = paperRevisionIntentService.suggest(request.content());
        if (suggestion != null) {
            List<AgentMessage> saved = new ArrayList<>();
            saved.add(messages.save(toAgentMessage(session.getId(), userId, ChatMessage.user(request.content()))));
            saved.add(messages.save(toAgentMessage(session.getId(), userId, ChatMessage.assistant(suggestion.assistantMessage()))));
            messages.flush();
            return new SendMessageResponse(
                    true,
                    suggestion.assistantMessage(),
                    0,
                    null,
                    suggestion.url(),
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
