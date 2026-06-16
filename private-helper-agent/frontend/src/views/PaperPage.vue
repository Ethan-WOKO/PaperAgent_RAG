<template>
  <AppLayout>
    <div class="paper-page workbench-page">
      <section class="workbench-hero">
        <div>
          <div class="workbench-kicker">Paper workflow</div>
          <h1>论文三步处理台</h1>
          <p>上传 docx 后订阅实时事件，处理完成即可下载结果文件。当前版本优先保障上传、任务状态、SSE 与下载链路稳定。</p>
        </div>
        <NTag :type="currentTask ? statusTagType(currentTask.status) : 'default'" round>
          {{ currentTask?.status || '未创建任务' }}
        </NTag>
      </section>

      <div class="paper-steps-bar">
        <div class="paper-step" :class="{ 'paper-step--active': !currentTask }">
          <span>1</span>
          <div><strong>上传论文</strong><small>选择 docx 与参数</small></div>
        </div>
        <div class="paper-step" :class="{ 'paper-step--active': currentTask && !canDownload }">
          <span>2</span>
          <div><strong>处理中</strong><small>查看 SSE 日志</small></div>
        </div>
        <div class="paper-step" :class="{ 'paper-step--active': canDownload }">
          <span>3</span>
          <div><strong>下载结果</strong><small>保存最终文件</small></div>
        </div>
      </div>

      <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
        <NGridItem span="24 l:8">
          <NCard class="workbench-card" :bordered="false">
            <template #header><div class="section-title">上传与参数</div></template>
            <NSpace vertical size="large">
              <NForm :model="form" label-placement="top">
                <NFormItem label="论文文件（.docx）">
                  <input ref="fileInputRef" type="file" accept=".docx" class="kb-file-input" @change="handleFileChange" />
                  <div class="upload-dropzone" @click="fileInputRef?.click()">
                    <strong>{{ selectedFile ? selectedFile.name : '点击选择 docx 文件' }}</strong>
                    <span>{{ selectedFile ? formatFileSize(selectedFile.size) : '支持论文原稿上传，后续流程将在右侧展示' }}</span>
                  </div>
                </NFormItem>
                <NGrid :cols="2" :x-gap="12">
                  <NFormItemGi label="目标语言">
                    <NSelect v-model:value="form.targetLanguage" :options="languageOptions" />
                  </NFormItemGi>
                  <NFormItemGi label="评分阈值">
                    <NInputNumber v-model:value="form.scoreThreshold" :min="0" :max="100" style="width: 100%" />
                  </NFormItemGi>
                  <NFormItemGi label="最大轮次">
                    <NInputNumber v-model:value="form.maxRounds" :min="1" :max="20" style="width: 100%" />
                  </NFormItemGi>
                  <NFormItemGi label="单节尝试">
                    <NInputNumber v-model:value="form.innerMaxAttempts" :min="1" :max="20" style="width: 100%" />
                  </NFormItemGi>
                  <NFormItemGi span="2" label="推荐文献数量">
                    <NInputNumber v-model:value="form.literatureCount" :min="1" :max="100" style="width: 100%" />
                  </NFormItemGi>
                </NGrid>
              </NForm>
              <NButton type="primary" block :loading="submitting" @click="handleSubmit">开始处理</NButton>
            </NSpace>
          </NCard>
        </NGridItem>

        <NGridItem span="24 l:10">
          <NCard class="workbench-card" :bordered="false">
            <template #header><div class="section-title">实时进度</div></template>
            <template #header-extra><NTag :type="sseStatus === 'connected' ? 'success' : 'default'">SSE {{ sseStatusText }}</NTag></template>
            <NSpace vertical size="large">
              <div class="paper-status-box status-grid">
                <div><span>任务 ID</span><strong>{{ currentTask?.id ?? '-' }}</strong></div>
                <div><span>当前状态</span><strong>{{ currentTask?.status ?? '-' }}</strong></div>
                <div><span>当前阶段</span><strong>{{ currentTask?.currentStage ?? '-' }}</strong></div>
                <div v-if="currentTask?.errorMessage" class="status-grid__wide"><span>错误信息</span><strong>{{ currentTask.errorMessage }}</strong></div>
              </div>
              <NSpace>
                <NButton secondary :disabled="!currentTaskId" @click="refreshTask">刷新</NButton>
                <NButton secondary :disabled="!currentTaskId" @click="connectSse">重连 SSE</NButton>
                <NButton tertiary :disabled="!canPause" @click="handlePause">暂停</NButton>
                <NButton tertiary :disabled="!canResume" @click="handleResume">继续</NButton>
                <NButton tertiary type="error" :disabled="!canStop" @click="handleStop">停止</NButton>
              </NSpace>
              <div class="paper-event-list timeline-list">
                <NEmpty v-if="events.length === 0" description="等待任务事件" />
                <div v-for="(event, index) in events" :key="`${event.timestamp}-${index}`" class="paper-event-item timeline-item">
                  <div class="paper-event-item__meta">{{ event.type }} · {{ event.stage || '-' }} · {{ formatDateTime(event.timestamp) }}</div>
                  <div>{{ event.message }}</div>
                </div>
              </div>
            </NSpace>
          </NCard>
        </NGridItem>

        <NGridItem span="24 l:6">
          <NCard class="workbench-card result-card" :bordered="false">
            <template #header><div class="section-title">结果下载</div></template>
            <NSpace vertical size="large">
              <NAlert type="info" title="当前说明">
                当前结果文件仍是链路验证版，用于先打通上传 → 事件 → 下载流程。
              </NAlert>
              <div class="paper-status-box">
                <div><strong>原始文件：</strong>{{ currentTask?.sourceFilename || '-' }}</div>
                <div><strong>结果文件：</strong>{{ currentTask?.finalObjectKey || '尚未生成' }}</div>
              </div>
              <NButton type="primary" block :loading="downloading" :disabled="!canDownload" @click="handleDownload">下载结果文件</NButton>
            </NSpace>
          </NCard>
        </NGridItem>
      </NGrid>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import {
  NAlert,
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NGridItem,
  NInputNumber,
  NSelect,
  NSpace,
  NTag,
} from 'naive-ui';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/components/AppLayout.vue';
import { createPaperTask, downloadPaperTask, getPaperTask, pausePaperTask, resumePaperTask, stopPaperTask, type PaperSseEvent, type PaperTaskResponse } from '@/api/paper';
import { ui } from '@/ui';

const route = useRoute();
const router = useRouter();
const fileInputRef = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const submitting = ref(false);
const downloading = ref(false);
const currentTask = ref<PaperTaskResponse | null>(null);
const events = ref<PaperSseEvent[]>([]);
const sseStatus = ref<'idle' | 'connecting' | 'connected' | 'closed' | 'error'>('idle');
let abortController: AbortController | null = null;

const form = reactive({
  targetLanguage: 'zh' as 'zh' | 'en',
  scoreThreshold: 75,
  maxRounds: 3,
  innerMaxAttempts: 2,
  literatureCount: 5,
});

const languageOptions = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const currentTaskId = computed(() => currentTask.value?.id ?? null);
const canPause = computed(() => currentTask.value?.status === 'RUNNING');
const canResume = computed(() => currentTask.value?.status === 'PAUSED');
const canStop = computed(() => ['PENDING', 'RUNNING', 'PAUSED'].includes(currentTask.value?.status || ''));
const canDownload = computed(() => Boolean(currentTask.value?.finalObjectKey) && !downloading.value);
const sseStatusText = computed(() => {
  if (sseStatus.value === 'connecting') return '连接中';
  if (sseStatus.value === 'connected') return '已连接';
  if (sseStatus.value === 'closed') return '已关闭';
  if (sseStatus.value === 'error') return '异常断开';
  return '未连接';
});

onMounted(async () => {
  const taskId = Number(route.query.taskId);
  if (!Number.isNaN(taskId) && taskId > 0) {
    await loadTask(taskId, true);
  }
});

watch(
  () => route.query.taskId,
  async (value) => {
    const taskId = Number(value);
    if (!Number.isNaN(taskId) && taskId > 0 && taskId !== currentTaskId.value) {
      events.value = [];
      await loadTask(taskId, true);
    }
  },
);

onBeforeUnmount(() => {
  abortController?.abort();
});

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
}

async function handleSubmit() {
  if (!selectedFile.value) {
    ui.message.warning('请先选择 docx 文件');
    return;
  }
  submitting.value = true;
  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);
    formData.append('targetLanguage', form.targetLanguage);
    formData.append('scoreThreshold', String(form.scoreThreshold));
    formData.append('maxRounds', String(form.maxRounds));
    formData.append('innerMaxAttempts', String(form.innerMaxAttempts));
    formData.append('literatureCount', String(form.literatureCount));
    const { data } = await createPaperTask(formData);
    currentTask.value = data;
    events.value = [];
    selectedFile.value = null;
    if (fileInputRef.value) {
      fileInputRef.value.value = '';
    }
    await router.replace({ path: '/paper', query: { taskId: String(data.id) } });
    connectSse();
    ui.message.success('论文任务已创建');
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '创建论文任务失败');
  } finally {
    submitting.value = false;
  }
}

async function loadTask(taskId: number, autoConnect = false) {
  try {
    const { data } = await getPaperTask(taskId);
    currentTask.value = data;
    if (autoConnect && ['PENDING', 'RUNNING', 'PAUSED'].includes(data.status)) {
      connectSse();
    }
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '加载论文任务失败');
  }
}

async function refreshTask() {
  if (!currentTaskId.value) return;
  await loadTask(currentTaskId.value, false);
}

function connectSse() {
  if (!currentTaskId.value) {
    return;
  }
  abortController?.abort();
  abortController = new AbortController();
  sseStatus.value = 'connecting';
  const token = localStorage.getItem('yanban_access_token');
  if (!token) {
    sseStatus.value = 'error';
    ui.message.error('未登录');
    return;
  }
  void streamPaperEvents(currentTaskId.value, token, abortController.signal);
}

async function streamPaperEvents(taskId: number, token: string, signal: AbortSignal) {
  try {
    const response = await fetch(`/api/v1/paper/events?taskId=${taskId}`, {
      headers: { Authorization: `Bearer ${token}` },
      signal,
    });
    if (!response.ok || !response.body) {
      throw new Error('SSE 连接失败');
    }
    sseStatus.value = 'connected';
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const chunks = buffer.split('\n\n');
      buffer = chunks.pop() || '';
      for (const chunk of chunks) {
        const event = parseSseChunk(chunk);
        if (event) {
          events.value.push(event);
          await loadTask(taskId, false);
          if (['complete', 'error', 'paused'].includes(event.type)) {
            abortController?.abort();
            sseStatus.value = 'closed';
            return;
          }
        }
      }
    }
  } catch (error: any) {
    if (error.name !== 'AbortError') {
      sseStatus.value = 'error';
      ui.message.warning(error.message || 'SSE 已断开');
      return;
    }
    sseStatus.value = 'closed';
  }
}

function parseSseChunk(chunk: string): PaperSseEvent | null {
  const lines = chunk.split('\n');
  let data = '';
  for (const line of lines) {
    if (line.startsWith('data:')) {
      data += line.slice(5).trim();
    }
  }
  if (!data) return null;
  try {
    return JSON.parse(data) as PaperSseEvent;
  } catch {
    return null;
  }
}

async function handlePause() {
  if (!currentTaskId.value) return;
  await pausePaperTask(currentTaskId.value);
  await refreshTask();
}

async function handleResume() {
  if (!currentTaskId.value) return;
  await resumePaperTask(currentTaskId.value);
  await refreshTask();
  connectSse();
}

async function handleStop() {
  if (!currentTaskId.value) return;
  await stopPaperTask(currentTaskId.value);
  await refreshTask();
}

async function handleDownload() {
  if (!currentTaskId.value) return;
  downloading.value = true;
  try {
    const { data, headers } = await downloadPaperTask(currentTaskId.value);
    const contentDisposition = String(headers['content-disposition'] || '');
    const matched = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
    const filename = decodeURIComponent(matched?.[1] || currentTask.value?.sourceFilename || 'paper-result.docx');
    const blobUrl = window.URL.createObjectURL(data);
    const anchor = document.createElement('a');
    anchor.href = blobUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.URL.revokeObjectURL(blobUrl);
    ui.message.success('下载已开始');
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '下载结果文件失败');
  } finally {
    downloading.value = false;
  }
}

function formatFileSize(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(2)} MB`;
}

function statusTagType(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED' || status === 'STOPPED') return 'error';
  if (status === 'RUNNING' || status === 'PENDING') return 'info';
  if (status === 'PAUSED') return 'warning';
  return 'default';
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}
</script>
