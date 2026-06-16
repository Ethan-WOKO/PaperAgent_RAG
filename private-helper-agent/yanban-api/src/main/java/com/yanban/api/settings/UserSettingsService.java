package com.yanban.api.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSettingsService {

    public static final String DEFAULT_PROVIDER = "deepseek";
    public static final String PROVIDER_GLM = "glm";
    public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    public static final String DEFAULT_GLM_MODEL = "glm-4.5-air";
    public static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.70");
    public static final int DEFAULT_MAX_STEPS = 20;
    public static final boolean DEFAULT_RAG_ENABLED = true;
    public static final List<String> DEFAULT_FILESYSTEM_ROOTS = List.of("workspace");

    private final SysUserSettingsRepository repository;
    private final SettingsCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public UserSettingsService(SysUserSettingsRepository repository,
                               SettingsCryptoService cryptoService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserSettingsResponse get(Long userId) {
        SysUserSettings settings = getOrCreate(userId);
        return toResponse(settings);
    }

    @Transactional
    public UserSettingsResponse update(Long userId, UserSettingsRequest request) {
        SysUserSettings settings = getOrCreate(userId);
        String provider = normalizeProvider(request.defaultProvider(), settings.getDefaultProvider());
        String deepseekModel = StringUtils.hasText(request.deepseekModel()) ? request.deepseekModel().trim() : settings.getDeepseekModel();
        String glmModel = StringUtils.hasText(request.glmModel()) ? request.glmModel().trim() : settings.getGlmModel();
        BigDecimal temperature = request.deepseekTemperature() != null ? request.deepseekTemperature() : settings.getDeepseekTemperature();
        Integer maxSteps = request.maxSteps() != null ? request.maxSteps() : settings.getMaxSteps();
        Boolean ragDefaultEnabled = request.ragDefaultEnabled() != null ? request.ragDefaultEnabled() : settings.getRagDefaultEnabled();
        String encryptedDeepseekApiKey = resolveEncryptedApiKey(settings.getDeepseekApiKeyEncrypted(), request.deepseekApiKey());
        String encryptedGlmApiKey = resolveEncryptedApiKey(settings.getGlmApiKeyEncrypted(), request.glmApiKey());
        String encryptedGithubPat = resolveEncryptedApiKey(settings.getGithubPatEncrypted(), request.githubPat());
        String filesystemRootsText = request.filesystemRoots() == null ? settings.getFilesystemRootsText() : writeJson(request.filesystemRoots());
        String disabledSkillsJson = request.disabledSkills() == null ? settings.getDisabledSkillsJson() : writeJson(request.disabledSkills());
        settings.update(provider, encryptedDeepseekApiKey, encryptedGlmApiKey, deepseekModel, glmModel,
                encryptedGithubPat, filesystemRootsText, disabledSkillsJson, temperature, maxSteps, ragDefaultEnabled);
        return toResponse(repository.saveAndFlush(settings));
    }

    @Transactional
    public SysUserSettings getOrCreate(Long userId) {
        return repository.findById(userId)
                .orElseGet(() -> repository.saveAndFlush(defaultSettings(userId)));
    }

    public String decryptDeepseekApiKey(SysUserSettings settings) {
        if (!StringUtils.hasText(settings.getDeepseekApiKeyEncrypted())) {
            return null;
        }
        return cryptoService.decrypt(settings.getDeepseekApiKeyEncrypted());
    }

    public String decryptGlmApiKey(SysUserSettings settings) {
        if (!StringUtils.hasText(settings.getGlmApiKeyEncrypted())) {
            return null;
        }
        return cryptoService.decrypt(settings.getGlmApiKeyEncrypted());
    }

    public String decryptGithubPat(SysUserSettings settings) {
        if (!StringUtils.hasText(settings.getGithubPatEncrypted())) {
            return null;
        }
        return cryptoService.decrypt(settings.getGithubPatEncrypted());
    }

    public List<String> parseFilesystemRoots(SysUserSettings settings) {
        return readStringList(settings.getFilesystemRootsText(), DEFAULT_FILESYSTEM_ROOTS);
    }

    public List<String> parseDisabledSkills(SysUserSettings settings) {
        return readStringList(settings.getDisabledSkillsJson(), List.of());
    }

    private UserSettingsResponse toResponse(SysUserSettings settings) {
        return UserSettingsResponse.from(settings, parseFilesystemRoots(settings), parseDisabledSkills(settings));
    }

    private SysUserSettings defaultSettings(Long userId) {
        return new SysUserSettings(
                userId,
                DEFAULT_PROVIDER,
                null,
                null,
                DEFAULT_DEEPSEEK_MODEL,
                DEFAULT_GLM_MODEL,
                null,
                writeJson(DEFAULT_FILESYSTEM_ROOTS),
                writeJson(List.of()),
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_STEPS,
                DEFAULT_RAG_ENABLED
        );
    }

    private String normalizeProvider(String provider, String fallback) {
        String resolved = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : fallback;
        if (!DEFAULT_PROVIDER.equals(resolved) && !PROVIDER_GLM.equals(resolved)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持 deepseek 或 glm 作为默认模型提供方");
        }
        return resolved;
    }

    private String resolveEncryptedApiKey(String existingEncryptedValue, String apiKey) {
        if (apiKey == null) {
            return existingEncryptedValue;
        }
        if (apiKey.isBlank()) {
            return null;
        }
        return cryptoService.encrypt(apiKey.trim());
    }

    private List<String> readStringList(String json, List<String> fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化设置失败", ex);
        }
    }
}
