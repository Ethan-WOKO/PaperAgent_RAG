<template>
  <AppLayout>
    <div class="kb-page workbench-page">
      <section class="workbench-hero">
        <div>
          <div class="workbench-kicker">Knowledge base</div>
          <h1>知识库文档工作台</h1>
          <p>上传科研资料后由后台异步解析、向量化并进入可检索状态。列表会在处理期间自动轮询。</p>
        </div>
        <NSpace>
          <NTag type="success" round>{{ readyCount }} READY</NTag>
          <NTag :type="hasProcessingDocuments ? 'warning' : 'default'" round>
            {{ hasProcessingDocuments ? '处理中' : '状态稳定' }}
          </NTag>
        </NSpace>
      </section>

      <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
        <NGridItem span="24 l:7">
          <NCard class="workbench-card" :bordered="false">
            <template #header><div class="section-title">上传文档</div></template>
            <NSpace vertical size="large">
              <NAlert type="info" title="分片上传">
                上传成功后文档先进入 PROCESSING，再由后台处理为 READY。
              </NAlert>

              <div class="kb-upload-box">
                <input ref="fileInputRef" type="file" class="kb-file-input" @change="handleFileChange" />
                <div class="upload-dropzone" @click="fileInputRef?.click()">
                  <strong>{{ selectedFile ? selectedFile.name : '点击选择文件' }}</strong>
                  <span>{{ selectedFile ? formatFileSize(selectedFile.size) : '支持 pdf / docx / txt / md 等文件' }}</span>
                </div>
              </div>

              <NCheckbox v-model:checked="isPublic">设为公开文档</NCheckbox>

              <div v-if="uploading" class="kb-progress-block">
                <NProgress type="line" :percentage="uploadProgress" :indicator-placement="'inside'" processing />
                <div class="chat-hint">{{ uploadStatusText }}</div>
              </div>

              <NSpace>
                <NButton type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">开始上传</NButton>
                <NButton quaternary :disabled="uploading || !selectedFile" @click="clearSelectedFile">清空</NButton>
              </NSpace>
            </NSpace>
          </NCard>
        </NGridItem>

        <NGridItem span="24 l:17">
          <NCard class="workbench-card" :bordered="false">
            <template #header><div class="section-title">文档列表</div></template>
            <template #header-extra>
              <NSpace>
                <NButton tertiary @click="router.push('/knowledge-base/search-debug')">检索调试</NButton>
                <NButton secondary :loading="loading" @click="loadDocuments">刷新</NButton>
              </NSpace>
            </template>

            <NEmpty v-if="documents.length === 0 && !loading" description="还没有知识库文档" />

            <div v-else class="kb-document-grid">
              <article v-for="item in documents" :key="item.id" class="kb-document-card">
                <div class="kb-document-card__main">
                  <div class="kb-filename">{{ item.filename }}</div>
                  <div class="chat-hint">#{{ item.id }} · {{ item.mimeType || '未知类型' }} · {{ formatFileSize(item.fileSize) }}</div>
                  <div v-if="item.errorMessage" class="kb-error-text">{{ item.errorMessage }}</div>
                </div>
                <div class="kb-document-card__side">
                  <NSpace justify="end">
                    <NTag :type="statusTagType(item.status)">{{ item.status }}</NTag>
                    <NTag :type="item.isPublic ? 'info' : 'default'">{{ item.isPublic ? '公开' : '私有' }}</NTag>
                  </NSpace>
                  <div class="chat-hint">{{ formatDateTime(item.updatedAt) }}</div>
                  <NPopconfirm @positive-click="handleDelete(item.id)">
                    <template #trigger><NButton text type="error">删除</NButton></template>
                    确认删除该文档？
                  </NPopconfirm>
                </div>
              </article>
            </div>
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
  NCheckbox,
  NEmpty,
  NGrid,
  NGridItem,
  NPopconfirm,
  NProgress,
  NSpace,
  NTag,
} from 'naive-ui';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/components/AppLayout.vue';
import { deleteKbDocument, listKbDocuments, mergeKbUpload, uploadChunk, type KbDocumentItem } from '@/api/knowledge';
import { ui } from '@/ui';

const CHUNK_SIZE = 1024 * 1024;

const router = useRouter();

const fileInputRef = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const isPublic = ref(false);
const uploading = ref(false);
const uploadProgress = ref(0);
const uploadStatusText = ref('');
const loading = ref(false);
const documents = ref<KbDocumentItem[]>([]);
let pollingTimer: number | null = null;

const hasProcessingDocuments = computed(() =>
  documents.value.some((item) => item.status === 'PROCESSING' || item.status === 'UPLOADING'),
);
const readyCount = computed(() => documents.value.filter((item) => item.status === 'READY').length);

onMounted(async () => {
  await loadDocuments();
});

onBeforeUnmount(() => {
  stopPolling();
});

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
}

function clearSelectedFile() {
  selectedFile.value = null;
  if (fileInputRef.value) {
    fileInputRef.value.value = '';
  }
}

async function handleUpload() {
  const file = selectedFile.value;
  if (!file) {
    ui.message.warning('请先选择文件');
    return;
  }

  const uploadId = globalThis.crypto?.randomUUID?.() || `upload-${Date.now()}`;
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

  uploading.value = true;
  uploadProgress.value = 0;

  try {
    for (let index = 0; index < totalChunks; index += 1) {
      const start = index * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      const chunk = file.slice(start, end);
      await uploadChunkWithRetry({
        uploadId,
        filename: file.name,
        chunkNumber: index,
        totalChunks,
        file: chunk,
      });
      uploadProgress.value = Math.round(((index + 1) / totalChunks) * 90);
    }

    uploadStatusText.value = '正在合并分片并提交后台处理';
    await mergeKbUpload({
      uploadId,
      filename: file.name,
      totalChunks,
      mimeType: file.type || 'application/octet-stream',
      isPublic: isPublic.value,
    });

    uploadProgress.value = 100;
    uploadStatusText.value = '上传完成，等待后台处理';
    ui.message.success('上传成功，文档正在处理中');
    clearSelectedFile();
    await loadDocuments();
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
}

async function loadDocuments() {
  loading.value = true;
  try {
    const { data } = await listKbDocuments();
    documents.value = data;
    if (data.some((item) => item.status === 'PROCESSING' || item.status === 'UPLOADING')) {
      ensurePolling();
    } else {
      stopPolling();
    }
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '加载知识库文档失败');
  } finally {
    loading.value = false;
  }
}

async function handleDelete(documentId: number) {
  try {
    await deleteKbDocument(documentId);
    ui.message.success('文档已删除');
    await loadDocuments();
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '删除文档失败');
  }
}

function ensurePolling() {
  if (pollingTimer !== null) {
    return;
  }
  pollingTimer = window.setInterval(() => {
    void loadDocuments();
  }, 3000);
}

function stopPolling() {
  if (pollingTimer !== null) {
    window.clearInterval(pollingTimer);
    pollingTimer = null;
  }
}

async function uploadChunkWithRetry(payload: {
  uploadId: string;
  filename: string;
  chunkNumber: number;
  totalChunks: number;
  file: Blob;
}) {
  let lastError: unknown;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      uploadStatusText.value = `正在上传分片 ${payload.chunkNumber + 1} / ${payload.totalChunks}（第 ${attempt} 次尝试）`;
      await uploadChunk(payload);
      return;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

function statusTagType(status: string) {
  if (status === 'READY') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'error';
  }
  if (status === 'PROCESSING' || status === 'UPLOADING') {
    return 'warning';
  }
  return 'default';
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString('zh-CN');
}

function formatFileSize(value: number | null) {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(2)} MB`;
}
</script>
