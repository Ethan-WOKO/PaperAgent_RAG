<template>
  <AppLayout>
    <div class="search-page workbench-page">
      <section class="workbench-hero">
        <div>
          <div class="workbench-kicker">Search debug</div>
          <h1>知识库检索调试</h1>
          <p>直接调用检索接口，观察当前 query 的命中文本、得分与权限可见性，用于验证 RAG 数据是否准备就绪。</p>
        </div>
        <NTag type="info" round>{{ results.length }} 条结果</NTag>
      </section>

      <NCard class="workbench-card search-panel" :bordered="false">
        <NSpace vertical size="large">
          <NForm :model="form" label-placement="top">
            <NGrid :cols="24" :x-gap="16" responsive="screen" item-responsive>
              <NFormItemGi span="24 m:18" label="Query">
                <NInput
                  v-model:value="form.query"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  placeholder="例如：实验室每周组会时间是什么时候？"
                />
              </NFormItemGi>

              <NFormItemGi span="24 m:6" label="topK">
                <NInputNumber v-model:value="form.topK" :min="1" :max="20" style="width: 100%" />
              </NFormItemGi>
            </NGrid>
          </NForm>

          <NSpace>
            <NButton type="primary" :loading="searching" @click="handleSearch">开始检索</NButton>
            <NButton quaternary @click="fillSampleQuery">填入示例</NButton>
            <NButton quaternary @click="clearResults">清空结果</NButton>
          </NSpace>
        </NSpace>
      </NCard>

      <NEmpty v-if="!searching && results.length === 0" description="还没有检索结果" class="empty-panel" />

      <div v-else class="search-debug-list result-list">
        <article v-for="(item, index) in results" :key="`${item.documentId}-${item.chunkIndex}-${index}`" class="search-result-card">
          <div class="search-result-card__rank">{{ index + 1 }}</div>
          <div class="search-result-card__body">
            <NSpace justify="space-between" align="center">
              <div>
                <strong>{{ item.filename }}</strong>
                <div class="chat-hint">documentId={{ item.documentId }} · chunkIndex={{ item.chunkIndex }}</div>
              </div>
              <NSpace>
                <NTag :type="item.isPublic ? 'info' : 'default'">{{ item.isPublic ? '公开' : '私有' }}</NTag>
                <NTag type="success">score {{ formatScore(item.score) }}</NTag>
              </NSpace>
            </NSpace>
            <div class="search-debug-content">{{ item.chunkText }}</div>
          </div>
        </article>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import {
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItemGi,
  NGrid,
  NInput,
  NInputNumber,
  NSpace,
  NTag,
} from 'naive-ui';
import { reactive, ref } from 'vue';
import AppLayout from '@/components/AppLayout.vue';
import { searchKnowledge, type KnowledgeSearchResult } from '@/api/knowledge';
import { ui } from '@/ui';

const form = reactive({
  query: '',
  topK: 5,
});
const searching = ref(false);
const results = ref<KnowledgeSearchResult[]>([]);

async function handleSearch() {
  if (!form.query.trim()) {
    ui.message.warning('请输入 query');
    return;
  }

  searching.value = true;
  try {
    const { data } = await searchKnowledge({
      query: form.query.trim(),
      topK: form.topK,
    });
    results.value = data;
    if (data.length === 0) {
      ui.message.info('未检索到结果');
    }
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '检索失败');
  } finally {
    searching.value = false;
  }
}

function fillSampleQuery() {
  form.query = '实验室每周组会时间是什么时候？';
  form.topK = 5;
}

function clearResults() {
  form.query = '';
  form.topK = 5;
  results.value = [];
}

function formatScore(score: number) {
  return Number(score).toFixed(4);
}
</script>
