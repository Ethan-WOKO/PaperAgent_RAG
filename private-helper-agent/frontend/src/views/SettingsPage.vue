<template>
  <AppLayout>
    <div class="settings-page workbench-page">
      <section class="workbench-hero">
        <div>
          <div class="workbench-kicker">Settings</div>
          <h1>设置中心</h1>
          <p>集中管理模型、Agent 行为、MCP 权限与 Skills。API Key 仅写入后端加密保存，不会在页面回显明文。</p>
        </div>
        <NButton type="primary" :loading="saving" @click="handleSave">保存设置</NButton>
      </section>

      <NForm :model="form" label-placement="top">
        <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
          <NGridItem span="24 l:12">
            <NCard class="workbench-card" :bordered="false">
              <template #header><div class="section-title">模型设置</div></template>
              <NGrid :cols="2" :x-gap="16" responsive="screen" item-responsive>
                <NFormItemGi span="2 m:1" label="默认 Provider"><NSelect v-model:value="form.defaultProvider" :options="providerOptions" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="temperature"><NInputNumber v-model:value="form.deepseekTemperature" :min="0" :max="2" :step="0.1" style="width: 100%" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="DeepSeek 模型"><NInput v-model:value="form.deepseekModel" placeholder="例如 deepseek-chat" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="DeepSeek API Key"><NInput v-model:value="form.deepseekApiKey" type="password" show-password-on="click" placeholder="留空表示不修改；输入空字符串并保存可清空" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="DeepSeek Key 状态"><NTag :type="deepseekConfigured ? 'success' : 'warning'">{{ deepseekConfigured ? '已配置' : '未配置' }}</NTag></NFormItemGi>
                <NFormItemGi span="2 m:1" label="GLM 模型"><NInput v-model:value="form.glmModel" placeholder="例如 glm-4.5-air" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="GLM API Key"><NInput v-model:value="form.glmApiKey" type="password" show-password-on="click" placeholder="留空表示不修改；输入空字符串并保存可清空" /></NFormItemGi>
                <NFormItemGi span="2 m:1" label="GLM Key 状态"><NTag :type="glmConfigured ? 'success' : 'warning'">{{ glmConfigured ? '已配置' : '未配置' }}</NTag></NFormItemGi>
              </NGrid>
            </NCard>
          </NGridItem>

          <NGridItem span="24 l:12">
            <NSpace vertical size="large">
              <NCard class="workbench-card" :bordered="false">
                <template #header><div class="section-title">Agent 设置</div></template>
                <NGrid :cols="2" :x-gap="16" responsive="screen" item-responsive>
                  <NFormItemGi span="2 m:1" label="max_steps"><NInputNumber v-model:value="form.maxSteps" :min="1" :max="100" style="width: 100%" /></NFormItemGi>
                  <NFormItemGi span="2 m:1" label="RAG 默认开关">
                    <NSpace vertical>
                      <NCheckbox v-model:checked="form.ragDefaultEnabled">默认启用知识库 RAG</NCheckbox>
                      <div class="chat-hint">关闭后，新建会话默认会以“禁用知识库”启动。</div>
                    </NSpace>
                  </NFormItemGi>
                </NGrid>
              </NCard>

              <NCard class="workbench-card" :bordered="false">
                <template #header><div class="section-title">MCP 设置</div></template>
                <NGrid :cols="2" :x-gap="16" responsive="screen" item-responsive>
                  <NFormItemGi span="2 m:1" label="GitHub PAT"><NInput v-model:value="form.githubPat" type="password" show-password-on="click" placeholder="用于 GitHub MCP；留空表示不修改" /></NFormItemGi>
                  <NFormItemGi span="2 m:1" label="GitHub PAT 状态"><NTag :type="githubConfigured ? 'success' : 'warning'">{{ githubConfigured ? '已配置' : '未配置' }}</NTag></NFormItemGi>
                  <NFormItemGi span="2" label="filesystem 允许根目录"><NInput v-model:value="filesystemRootsText" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="每行一个目录，例如&#10;workspace&#10;D:/papers" /></NFormItemGi>
                </NGrid>
              </NCard>
            </NSpace>
          </NGridItem>

          <NGridItem span="24">
            <NCard class="workbench-card" :bordered="false">
              <template #header><div class="section-title">Skills</div></template>
              <template #header-extra><span class="chat-hint">用户 Skill 请手动放入 skills/user/ 目录</span></template>
              <div class="skill-grid">
                <NEmpty v-if="skills.length === 0" description="未扫描到 Skills" />
                <article v-for="skill in skills" :key="skill.id" class="skill-card">
                  <div>
                    <div><strong>{{ skill.name }}</strong> <NTag size="small">{{ skill.source }}</NTag></div>
                    <div class="chat-hint">{{ skill.path }}</div>
                    <div class="chat-hint">{{ skill.description }}</div>
                  </div>
                  <NCheckbox :checked="!disabledSkillsSet.has(skill.id)" @update:checked="(checked) => toggleSkill(skill.id, checked)">启用</NCheckbox>
                </article>
              </div>
            </NCard>
          </NGridItem>
        </NGrid>
      </NForm>

      <div class="settings-footer-bar">
        <span class="chat-hint">最近更新时间：{{ updatedAtText }}</span>
        <NButton type="primary" :loading="saving" @click="handleSave">保存设置</NButton>
      </div>
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
  NFormItemGi,
  NGrid,
  NGridItem,
  NInput,
  NInputNumber,
  NSelect,
  NSpace,
  NTag,
} from 'naive-ui';
import { computed, onMounted, reactive, ref } from 'vue';
import AppLayout from '@/components/AppLayout.vue';
import { listSkills, type SkillListItemResponse } from '@/api/skills';
import { getSettings, updateSettings } from '@/api/settings';
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
  deepseekTemperature: 0.7,
  maxSteps: 20,
  ragDefaultEnabled: true,
});

const disabledSkillsSet = computed(() => new Set(disabledSkills.value));
const updatedAtText = computed(() => updatedAt.value ? new Date(updatedAt.value).toLocaleString('zh-CN') : '暂无');

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
    form.deepseekTemperature = data.deepseekTemperature;
    form.maxSteps = data.maxSteps;
    form.ragDefaultEnabled = data.ragDefaultEnabled;
    filesystemRootsText.value = (data.filesystemRoots || []).join('\n');
    disabledSkills.value = [...(data.disabledSkills || [])];
    deepseekConfigured.value = data.deepseekApiKeyConfigured;
    glmConfigured.value = data.glmApiKeyConfigured;
    githubConfigured.value = data.githubPatConfigured;
    updatedAt.value = data.updatedAt;
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '加载设置失败');
  }
}

async function loadSkills() {
  try {
    const { data } = await listSkills();
    skills.value = data;
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '加载 Skills 失败');
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
    ui.message.success('设置已保存');
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '保存设置失败');
  } finally {
    saving.value = false;
  }
}

function splitLines(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}
</script>
