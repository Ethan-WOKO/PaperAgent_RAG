package com.yanban.api.demo;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "yanban.demo")
public class DemoProperties {

    private boolean enabled = false;
    private boolean seedOnStartup = true;
    private String username = "demo";
    private String canonicalUrl = "http://yanban.online:18080/demo";
    private long maxUploadBytes = 3L * 1024 * 1024;
    private int maxUploadsPerReset = 5;
    private int maxChatMessagesPerHour = 60;
    private int maxPaperTasksPerReset = 2;
    private List<String> exampleQuestions = List.of(
            "根据演示知识库，这个项目主要解决哪三类科研工作流问题？",
            "演示文档里的组会时间、会议地点和下次 DDL 分别是什么？",
            "根据 RAG 笔记，系统从文档上传到生成回答经历哪些步骤？",
            "如果我要在两周内改进 Agent 体验，演示文档建议优先做哪些任务？"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSeedOnStartup() {
        return seedOnStartup;
    }

    public void setSeedOnStartup(boolean seedOnStartup) {
        this.seedOnStartup = seedOnStartup;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public int getMaxUploadsPerReset() {
        return maxUploadsPerReset;
    }

    public void setMaxUploadsPerReset(int maxUploadsPerReset) {
        this.maxUploadsPerReset = maxUploadsPerReset;
    }

    public int getMaxChatMessagesPerHour() {
        return maxChatMessagesPerHour;
    }

    public void setMaxChatMessagesPerHour(int maxChatMessagesPerHour) {
        this.maxChatMessagesPerHour = maxChatMessagesPerHour;
    }

    public int getMaxPaperTasksPerReset() {
        return maxPaperTasksPerReset;
    }

    public void setMaxPaperTasksPerReset(int maxPaperTasksPerReset) {
        this.maxPaperTasksPerReset = maxPaperTasksPerReset;
    }

    public List<String> getExampleQuestions() {
        return exampleQuestions;
    }

    public void setExampleQuestions(List<String> exampleQuestions) {
        if (exampleQuestions != null && !exampleQuestions.isEmpty()) {
            this.exampleQuestions = exampleQuestions;
        }
    }
}
