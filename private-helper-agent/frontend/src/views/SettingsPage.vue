<template>
  <AppLayout>
    <div class="settings-page workbench-page scholar-page scholar-page--settings">
      <section class="workbench-hero scholar-page-hero">
        <div>
          <div class="workbench-kicker">Settings</div>
          <h1>Settings</h1>
          <p>Configure model providers, agent behavior, MCP permissions, skills, and credentials without exposing secrets.</p>
        </div>
        <NSpace align="center">
          <span class="chat-hint">Last updated: {{ updatedAtText }}</span>
          <NButton type="primary" :loading="saving" @click="handleSave">Save settings</NButton>
        </NSpace>
      </section>

      <NForm :model="form" label-placement="top">
        <NSpace vertical size="large">
          <NCard class="workbench-card scholar-card settings-section-card" :bordered="false">
            <template #header>
              <div class="section-title">Model Providers</div>
            </template>

            <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
              <NGridItem span="24 l:9">
                <article class="settings-provider-card">
                  <div class="settings-provider-card__head">
                    <div class="settings-provider-mark settings-provider-mark--deepseek">DS</div>
                    <div>
                      <strong>DeepSeek</strong>
                      <span>Reasoning and drafting provider</span>
                    </div>
                    <NTag :type="deepseekConfigured ? 'success' : 'warning'" round>
                      {{ deepseekConfigured ? 'API key configured' : 'API key missing' }}
                    </NTag>
                  </div>
                  <NGrid :cols="2" :x-gap="12" responsive="screen" item-responsive>
                    <NFormItemGi span="2 m:1" label="Model name">
                      <NSelect v-model:value="form.deepseekModel" filterable tag :options="deepseekModelOptions" />
                    </NFormItemGi>
                    <NFormItemGi span="2 m:1" label="Available models">
                      <NDynamicTags v-model:value="form.deepseekModels" :max="20" />
                    </NFormItemGi>
                    <NFormItemGi span="2 m:1" label="API Key">
                      <NInput
                        v-model:value="form.deepseekApiKey"
                        type="password"
                        show-password-on="click"
                        placeholder="Leave blank to keep current key"
                      />
                    </NFormItemGi>
                  </NGrid>
                </article>
              </NGridItem>

              <NGridItem span="24 l:9">
                <article class="settings-provider-card">
                  <div class="settings-provider-card__head">
                    <div class="settings-provider-mark settings-provider-mark--glm">GL</div>
                    <div>
                      <strong>GLM</strong>
                      <span>Alternate provider for evaluation</span>
                    </div>
                    <NTag :type="glmConfigured ? 'success' : 'warning'" round>
                      {{ glmConfigured ? 'API key configured' : 'API key missing' }}
                    </NTag>
                  </div>
                  <NGrid :cols="2" :x-gap="12" responsive="screen" item-responsive>
                    <NFormItemGi span="2 m:1" label="Model name">
                      <NSelect v-model:value="form.glmModel" filterable tag :options="glmModelOptions" />
                    </NFormItemGi>
                    <NFormItemGi span="2 m:1" label="Available models">
                      <NDynamicTags v-model:value="form.glmModels" :max="20" />
                    </NFormItemGi>
                    <NFormItemGi span="2 m:1" label="API Key">
                      <NInput
                        v-model:value="form.glmApiKey"
                        type="password"
                        show-password-on="click"
                        placeholder="Leave blank to keep current key"
                      />
                    </NFormItemGi>
                  </NGrid>
                </article>
              </NGridItem>

              <NGridItem span="24 l:6">
                <article class="settings-default-card">
                  <NFormItem label="Default provider">
                    <NSelect v-model:value="form.defaultProvider" :options="providerOptions" />
                  </NFormItem>
                  <NFormItem label="Temperature">
                    <NInputNumber v-model:value="form.deepseekTemperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
                  </NFormItem>
                  <NFormItem label="Max plan steps">
                    <NInputNumber v-model:value="form.maxSteps" :min="1" :max="100" style="width: 100%" />
                  </NFormItem>
                </article>
              </NGridItem>
            </NGrid>
          </NCard>

          <NCard class="workbench-card scholar-card settings-section-card" :bordered="false">
            <template #header>
              <div class="section-title">Web Search Provider</div>
            </template>
            <div class="settings-search-provider">
              <div class="settings-provider-mark settings-provider-mark--tavily">TV</div>
              <div>
                <strong>Tavily / formal web search</strong>
                <span>Configured through backend environment variables such as WEB_SEARCH_PROVIDER and TAVILY_API_KEY.</span>
              </div>
              <NTag type="info" round>Credit-saving defaults enabled</NTag>
              <div class="settings-search-provider__controls">
                <div>
                  <span>Search depth</span>
                  <strong>basic by default</strong>
                </div>
                <div>
                  <span>Cache TTL</span>
                  <strong>15 min default</strong>
                </div>
                <div>
                  <span>Max results</span>
                  <strong>8 default</strong>
                </div>
              </div>
            </div>
          </NCard>

          <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
            <NGridItem span="24 l:12">
              <NCard class="workbench-card scholar-card settings-section-card" :bordered="false">
                <template #header>
                  <div class="section-title">Agent and MCP / Tools</div>
                </template>
                <NSpace vertical size="large">
                  <div class="settings-toggle-row">
                    <div>
                      <strong>Knowledge Base RAG</strong>
                      <span>New sessions can use private retrieval by default.</span>
                    </div>
                    <NSwitch v-model:value="form.ragDefaultEnabled" />
                  </div>
                  <div class="settings-tool-row">
                    <div class="settings-provider-mark settings-provider-mark--github">GH</div>
                    <div>
                      <strong>GitHub MCP</strong>
                      <span>Repository access for code and documentation workflows.</span>
                    </div>
                    <NTag :type="githubConfigured ? 'success' : 'warning'" round>
                      {{ githubConfigured ? 'PAT configured' : 'PAT missing' }}
                    </NTag>
                  </div>
                  <NFormItem label="GitHub PAT">
                    <NInput
                      v-model:value="form.githubPat"
                      type="password"
                      show-password-on="click"
                      placeholder="Leave blank to keep current token"
                    />
                  </NFormItem>
                  <NFormItem label="Filesystem allowed roots">
                    <NInput
                      v-model:value="filesystemRootsText"
                      type="textarea"
                      :autosize="{ minRows: 4, maxRows: 7 }"
                      placeholder="One path per line, for example: workspace"
                    />
                  </NFormItem>
                </NSpace>
              </NCard>
            </NGridItem>

            <NGridItem span="24 l:12">
              <NCard class="workbench-card scholar-card settings-section-card" :bordered="false">
                <template #header>
                  <div class="section-title">Skills</div>
                </template>
                <template #header-extra>
                  <span class="chat-hint">Loaded from backend skill registry</span>
                </template>
                <div class="settings-skill-grid">
                  <NEmpty v-if="skills.length === 0" description="No skills found." />
                  <article v-for="skill in skills" :key="skill.id" class="settings-skill-pill">
                    <div>
                      <strong>{{ skill.name }}</strong>
                      <span>{{ skill.source }}</span>
                    </div>
                    <NCheckbox :checked="!disabledSkillsSet.has(skill.id)" @update:checked="(checked) => toggleSkill(skill.id, checked)">
                      Enabled
                    </NCheckbox>
                  </article>
                </div>
              </NCard>
            </NGridItem>
          </NGrid>

          <NCard class="workbench-card scholar-card settings-section-card" :bordered="false">
            <template #header>
              <div class="section-title">Custom Models</div>
            </template>
            <template #header-extra>
              <NButton size="small" type="primary" @click="openCreateModelModal">+ 添加模型</NButton>
            </template>
            <div class="settings-skill-grid">
              <NEmpty v-if="customModels.filter((m) => !m.builtin).length === 0" description="尚未添加自定义模型。点击右上角添加。" />
              <article v-for="model in customModels.filter((m) => !m.builtin)" :key="model.id" class="settings-skill-pill">
                <div>
                  <strong>{{ model.label }}</strong>
                  <span>{{ model.modelName }}</span>
                  <span class="chat-hint">{{ model.apiUrl }}</span>
                </div>
                <NSpace>
                  <NTag :type="model.apiKeyConfigured ? 'success' : 'warning'" round size="small">
                    {{ model.apiKeyConfigured ? 'Key set' : 'Key missing' }}
                  </NTag>
                  <NButton size="small" :loading="testingModelId === model.id" @click="handleTestModel(model)">测试</NButton>
                  <NButton size="small" secondary @click="openEditModelModal(model)">编辑</NButton>
                  <NButton size="small" tertiary type="error" @click="handleDeleteModel(model)">删除</NButton>
                </NSpace>
              </article>
            </div>
          </NCard>

          <NAlert type="warning" class="settings-security-note" title="Security Notes">
            Do not expose API keys in prompts, client-side code, screenshots, or generated reports. Rotate credentials regularly.
          </NAlert>

          <div class="settings-footer-bar">
            <span class="chat-hint">Changes are saved to the backend settings store. Blank secret fields keep existing values.</span>
            <NButton type="primary" :loading="saving" @click="handleSave">Save settings</NButton>
          </div>
        </NSpace>
      </NForm>

      <NModal v-model:show="modelModalVisible" preset="card" :title="editingModelId ? '编辑自定义模型' : '添加自定义模型'" style="width: 520px" :bordered="false">
        <NForm label-placement="top">
          <NFormItem label="模型名称（显示用）">
            <NInput v-model:value="modelForm.label" placeholder="例如：我的 DeepSeek V4 Pro" />
          </NFormItem>
          <NFormItem label="API 地址">
            <NInput v-model:value="modelForm.apiUrl" placeholder="https://api.deepseek.com/v1/chat/completions" />
          </NFormItem>
          <NFormItem label="模型 ID">
            <NInput v-model:value="modelForm.modelName" placeholder="例如：deepseek-chat" />
          </NFormItem>
          <NFormItem :label="editingModelId ? 'API Key（留空保持不变）' : 'API Key'">
            <NInput v-model:value="modelForm.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
          </NFormItem>
          <NSpace justify="end">
            <NButton @click="modelModalVisible = false">取消</NButton>
            <NButton type="primary" @click="handleSaveModel">保存</NButton>
          </NSpace>
        </NForm>
      </NModal>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NEmpty,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NGridItem,
  NInput,
  NInputNumber,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  NModal,
  NDynamicTags,
} from 'naive-ui';
import { computed, onMounted, reactive, ref } from 'vue';
import AppLayout from '@/components/AppLayout.vue';
import { listSkills, type SkillListItemResponse } from '@/api/skills';
import { getSettings, updateSettings, createModel, updateModel, deleteModel, testModel, type UserModelResponse } from '@/api/settings';
import { ui } from '@/ui';

const providerOptions = [
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'GLM', value: 'glm' },
];

const saving = ref(false);
const deepseekConfigured = ref(false);
const glmConfigured = ref(false);
const githubConfigured = ref(false);
const updatedAt = ref<string | null>(null);
const filesystemRootsText = ref('workspace');
const skills = ref<SkillListItemResponse[]>([]);
const disabledSkills = ref<string[]>([]);
const form = reactive({
  defaultProvider: 'deepseek',
  deepseekApiKey: '',
  glmApiKey: '',
  githubPat: '',
  deepseekModel: 'deepseek-chat',
  glmModel: 'glm-4.5-air',
  deepseekModels: ['deepseek-chat', 'deepseek-reasoner'] as string[],
  glmModels: ['glm-4.5-air', 'glm-4-flash'] as string[],
  deepseekTemperature: 0.7,
  maxSteps: 20,
  ragDefaultEnabled: true,
});

// Custom models state
const customModels = ref<UserModelResponse[]>([]);
const modelModalVisible = ref(false);
const editingModelId = ref<number | null>(null);
const modelForm = reactive({ label: '', apiUrl: '', apiKey: '', modelName: '' });
const testingModelId = ref<number | null>(null);

const disabledSkillsSet = computed(() => new Set(disabledSkills.value));
const updatedAtText = computed(() => updatedAt.value ? new Date(updatedAt.value).toLocaleString('zh-CN') : 'Never');
const deepseekModelOptions = computed(() => form.deepseekModels.map((m) => ({ label: m, value: m })));
const glmModelOptions = computed(() => form.glmModels.map((m) => ({ label: m, value: m })));
const builtinModels = computed(() => customModels.value.filter((m) => m.builtin));

onMounted(async () => {
  await Promise.all([loadSettings(), loadSkills()]);
});

async function loadSettings() {
  try {
    const { data } = await getSettings();
    form.defaultProvider = data.defaultProvider;
    form.deepseekApiKey = '';
    form.glmApiKey = '';
    form.githubPat = '';
    form.deepseekModel = data.deepseekModel;
    form.glmModel = data.glmModel;
    form.deepseekModels = data.deepseekModels?.length ? data.deepseekModels : ['deepseek-chat', 'deepseek-reasoner'];
    form.glmModels = data.glmModels?.length ? data.glmModels : ['glm-4.5-air', 'glm-4-flash'];
    form.deepseekTemperature = data.deepseekTemperature;
    form.maxSteps = data.maxSteps;
    form.ragDefaultEnabled = data.ragDefaultEnabled;
    filesystemRootsText.value = (data.filesystemRoots || []).join('\n');
    disabledSkills.value = [...(data.disabledSkills || [])];
    deepseekConfigured.value = data.deepseekApiKeyConfigured;
    glmConfigured.value = data.glmApiKeyConfigured;
    githubConfigured.value = data.githubPatConfigured;
    updatedAt.value = data.updatedAt;
    customModels.value = data.customModels || [];
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || 'Failed to load settings.');
  }
}

async function loadSkills() {
  try {
    const { data } = await listSkills();
    skills.value = data;
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || 'Failed to load skills.');
  }
}

function toggleSkill(skillId: string, enabled: boolean) {
  if (enabled) {
    disabledSkills.value = disabledSkills.value.filter((item) => item !== skillId);
  } else if (!disabledSkills.value.includes(skillId)) {
    disabledSkills.value = [...disabledSkills.value, skillId];
  }
}

async function handleSave() {
  saving.value = true;
  try {
    const { data } = await updateSettings({
      defaultProvider: form.defaultProvider,
      deepseekApiKey: form.deepseekApiKey,
      glmApiKey: form.glmApiKey,
      githubPat: form.githubPat,
      deepseekModel: form.deepseekModel,
      glmModel: form.glmModel,
      deepseekModels: form.deepseekModels,
      glmModels: form.glmModels,
      deepseekTemperature: form.deepseekTemperature,
      maxSteps: form.maxSteps,
      ragDefaultEnabled: form.ragDefaultEnabled,
      filesystemRoots: splitLines(filesystemRootsText.value),
      disabledSkills: disabledSkills.value,
    });
    form.deepseekApiKey = '';
    form.glmApiKey = '';
    form.githubPat = '';
    deepseekConfigured.value = data.deepseekApiKeyConfigured;
    glmConfigured.value = data.glmApiKeyConfigured;
    githubConfigured.value = data.githubPatConfigured;
    updatedAt.value = data.updatedAt;
    filesystemRootsText.value = (data.filesystemRoots || []).join('\n');
    disabledSkills.value = [...(data.disabledSkills || [])];
    ui.message.success('Settings saved.');
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || 'Failed to save settings.');
  } finally {
    saving.value = false;
  }
}

function splitLines(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

// ===== Custom model management =====

function openCreateModelModal() {
  editingModelId.value = null;
  modelForm.label = '';
  modelForm.apiUrl = '';
  modelForm.apiKey = '';
  modelForm.modelName = '';
  modelModalVisible.value = true;
}

function openEditModelModal(model: UserModelResponse) {
  editingModelId.value = model.id;
  modelForm.label = model.label;
  modelForm.apiUrl = model.apiUrl || '';
  modelForm.apiKey = '';
  modelForm.modelName = model.modelName;
  modelModalVisible.value = true;
}

async function handleSaveModel() {
  if (!modelForm.label || !modelForm.apiUrl || !modelForm.modelName) {
    ui.message.warning('请填写模型名称、API 地址和模型 ID');
    return;
  }
  try {
    const payload = {
      label: modelForm.label,
      apiUrl: modelForm.apiUrl,
      apiKey: modelForm.apiKey || undefined,
      modelName: modelForm.modelName,
    };
    if (editingModelId.value) {
      await updateModel(editingModelId.value, payload);
      ui.message.success('模型已更新');
    } else {
      await createModel(payload);
      ui.message.success('模型已添加');
    }
    modelModalVisible.value = false;
    await loadSettings();
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '保存模型失败');
  }
}

async function handleDeleteModel(model: UserModelResponse) {
  try {
    await deleteModel(model.id);
    ui.message.success('模型已删除');
    await loadSettings();
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '删除模型失败');
  }
}

async function handleTestModel(model: UserModelResponse) {
  testingModelId.value = model.id;
  try {
    const { data } = await testModel(model.id);
    if (data.success) {
      ui.message.success(`连接成功${data.content ? '：' + data.content : ''}`);
    } else {
      ui.message.error(`连接失败：${data.error || '未知错误'}`);
    }
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '测试失败');
  } finally {
    testingModelId.value = null;
  }
}
</script>
