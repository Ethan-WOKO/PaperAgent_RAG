<template>
  <NLayout class="app-layout">
    <NLayoutHeader bordered class="app-header">
      <div class="app-header__brand" @click="router.push('/chat')">
        <div class="app-header__logo">研</div>
        <div>
          <div class="app-title">研伴 Agent</div>
          <div class="app-subtitle">Research co-pilot workspace</div>
        </div>
      </div>

      <div class="app-header__nav">
        <NButton quaternary round class="app-nav-button" @click="router.push('/chat')">对话</NButton>
        <NButton quaternary round class="app-nav-button" @click="router.push('/paper')">论文</NButton>
        <NButton quaternary round class="app-nav-button" @click="router.push('/knowledge-base')">知识库</NButton>
        <NButton quaternary round class="app-nav-button" @click="router.push('/knowledge-base/search-debug')">检索调试</NButton>
        <NButton quaternary round class="app-nav-button" @click="router.push('/settings')">设置</NButton>
      </div>

      <NSpace align="center" :size="12">
        <NButton quaternary round class="theme-toggle-button" @click="toggleTheme">
          {{ isDark ? '浅色' : '深色' }}
        </NButton>
        <div class="app-user-chip">{{ authStore.currentUser?.username || '未登录' }}</div>
        <NButton tertiary type="error" round @click="logout">退出</NButton>
      </NSpace>
    </NLayoutHeader>

    <NLayoutContent content-class="app-content-shell">
      <slot />
    </NLayoutContent>
  </NLayout>
</template>

<script setup lang="ts">
import { NButton, NLayout, NLayoutContent, NLayoutHeader, NSpace } from 'naive-ui';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useTheme } from '@/composables/useTheme';

const router = useRouter();
const authStore = useAuthStore();
const { isDark, toggleTheme } = useTheme();

async function logout() {
  authStore.clear();
  await router.push('/login');
}
</script>
