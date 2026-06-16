package com.yanban.api.settings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UserSettingsResponse(
        String defaultProvider,
        boolean deepseekApiKeyConfigured,
        boolean glmApiKeyConfigured,
        boolean githubPatConfigured,
        String deepseekModel,
        String glmModel,
        BigDecimal deepseekTemperature,
        Integer maxSteps,
        Boolean ragDefaultEnabled,
        List<String> filesystemRoots,
        List<String> disabledSkills,
        Instant updatedAt
) {
    public static UserSettingsResponse from(SysUserSettings settings, List<String> filesystemRoots, List<String> disabledSkills) {
        return new UserSettingsResponse(
                settings.getDefaultProvider(),
                settings.getDeepseekApiKeyEncrypted() != null && !settings.getDeepseekApiKeyEncrypted().isBlank(),
                settings.getGlmApiKeyEncrypted() != null && !settings.getGlmApiKeyEncrypted().isBlank(),
                settings.getGithubPatEncrypted() != null && !settings.getGithubPatEncrypted().isBlank(),
                settings.getDeepseekModel(),
                settings.getGlmModel(),
                settings.getDeepseekTemperature(),
                settings.getMaxSteps(),
                settings.getRagDefaultEnabled(),
                filesystemRoots,
                disabledSkills,
                settings.getUpdatedAt()
        );
    }
}
