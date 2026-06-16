package com.yanban.core.model;

import java.util.Map;
import reactor.core.publisher.Flux;

public class RoutingChatModelProvider implements ChatModelProvider {

    private final Map<String, ChatModelProvider> providers;
    private final String defaultProvider;

    public RoutingChatModelProvider(Map<String, ChatModelProvider> providers, String defaultProvider) {
        this.providers = Map.copyOf(providers);
        this.defaultProvider = defaultProvider;
    }

    @Override
    public String providerName() {
        return "routing";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return resolve(request.provider()).chat(request);
    }

    @Override
    public Flux<ChatChunk> streamChat(ChatRequest request) {
        return resolve(request.provider()).streamChat(request);
    }

    private ChatModelProvider resolve(String provider) {
        String resolved = (provider == null || provider.isBlank()) ? defaultProvider : provider.trim().toLowerCase();
        ChatModelProvider modelProvider = providers.get(resolved);
        if (modelProvider == null) {
            throw new ModelProviderException("Unsupported model provider: " + resolved);
        }
        return modelProvider;
    }
}
