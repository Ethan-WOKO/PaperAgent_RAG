package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:yanban_agent_controller_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "yanban.jwt.secret=test_secret_123456789012345678901234567890"
})
class AgentControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean(name = "chatModelProvider")
    ChatModelProvider chatModelProvider;

    @Test
    void createSessionSendMessageAndListPersistedMessages() throws Exception {
        when(chatModelProvider.providerName()).thenReturn("mock");
        when(chatModelProvider.chat(any())).thenReturn(
                new ChatResponse(ChatMessage.assistant("你好，我是研伴。"), "stop", null)
        );
        String token = registerAndGetToken("agent_user_a");

        long sessionId = createSession(token, "测试会话");

        mockMvc.perform(post("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.assistantContent").value("你好，我是研伴。"))
                .andExpect(jsonPath("$.messages.length()").value(2));

        MvcResult messagesResult = mockMvc.perform(get("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("你好"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").value("你好，我是研伴。"))
                .andReturn();

        JsonNode messages = objectMapper.readTree(messagesResult.getResponse().getContentAsString());
        assertThat(messages.get(0).get("sessionId").asLong()).isEqualTo(sessionId);
        assertThat(messages.get(1).get("sessionId").asLong()).isEqualTo(sessionId);
    }

    @Test
    void userCannotAccessAnotherUsersSession() throws Exception {
        String tokenA = registerAndGetToken("agent_user_owner");
        String tokenB = registerAndGetToken("agent_user_other");
        long sessionId = createSession(tokenA, "私有会话");

        mockMvc.perform(get("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权访问\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void paperRevisionIntentReturnsPaperLink() throws Exception {
        String token = registerAndGetToken("agent_user_paper_intent");
        long sessionId = createSession(token, "论文会话");

        mockMvc.perform(post("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"帮我润色论文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.navigationUrl").value("/paper"))
                .andExpect(jsonPath("$.assistantContent").value(org.hamcrest.Matchers.containsString("/paper")));

        mockMvc.perform(get("/api/v1/agent/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].content").value(org.hamcrest.Matchers.containsString("/paper")));
    }

    @Test
    void listSessionsOnlyReturnsCurrentUsersSessions() throws Exception {
        String tokenA = registerAndGetToken("agent_user_list_a");
        String tokenB = registerAndGetToken("agent_user_list_b");
        createSession(tokenA, "A 的会话");
        createSession(tokenB, "B 的会话");

        mockMvc.perform(get("/api/v1/agent/sessions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("A 的会话"));
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createSession(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"maxSteps\":20}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
